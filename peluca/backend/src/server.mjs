import http from 'node:http';
import { handleFeed } from './recommendations.mjs';
import { db, all, one, run, transaction, id, hashPassword, verifyPassword, tokenHash, newSession, publicUser, appointmentJSON, cutCategories } from './db.mjs';

const port=Number(process.env.PORT||8080), host=process.env.HOST||'127.0.0.1';
class ApiError extends Error {constructor(status,message){super(message);this.status=status;}}
const fail=(status,msg)=>{throw new ApiError(status,msg);};
const str=(v,label,min=1,max=120)=>{if(typeof v!=='string'||v.trim().length<min||v.trim().length>max)fail(400,`${label}: valor no válido.`);return v.trim();};
const integer=(v,label,min,max)=>{if(!Number.isInteger(v)||v<min||v>max)fail(400,`${label}: valor no válido.`);return v;};
const emailValue=v=>{const e=str(v,'Correo',3,254).toLowerCase();if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e))fail(400,'Correo no válido.');return e;};
const passwordValue=v=>{if(typeof v!=='string'||v.length<8||v.length>128)fail(400,'Usa una contraseña de 8 a 128 caracteres.');return v;};
const owner=u=>{if(u.role!=='owner')fail(403,'Esta acción requiere acceso del dueño.');};
const catalog=()=>({services:all('SELECT * FROM services ORDER BY id').map(s=>({...s,active:!!s.active})),barbers:all('SELECT * FROM barbers ORDER BY id').map(b=>({...b,active:!!b.active})),...hours()});
const hours=()=>{const s=one('SELECT * FROM settings WHERE id=1');return {openHour:s.open_hour,closeHour:s.close_hour,closedWeekdays:JSON.parse(s.closed),timezone:'America/Mazatlan'};};
function snapshot(u){return {...catalog(),user:publicUser(u),appointments:all(`SELECT a.*,u.email FROM appointments a LEFT JOIN users u ON a.user_id=u.id ${u.role==='owner'?'':'WHERE a.user_id=?'} ORDER BY start`,...(u.role==='owner'?[]:[u.id])).map(appointmentJSON),favorites:all('SELECT cut FROM favorites WHERE user_id=?',u.id).map(x=>x.cut),likedPosts:all("SELECT post_id FROM reactions WHERE user_id=? AND kind='like'",u.id).map(x=>x.post_id),savedPosts:all("SELECT post_id FROM reactions WHERE user_id=? AND kind='save'",u.id).map(x=>x.post_id),publications:all('SELECT body FROM posts WHERE user_id=? ORDER BY created DESC',u.id).map(x=>x.body)};}
const limits=new Map();
function throttle(key,max){const now=Date.now();let x=limits.get(key);if(!x||x.until<now)x={n:0,until:now+60000};x.n++;limits.set(key,x);if(x.n>max)fail(429,'Demasiadas solicitudes. Espera un minuto.');if(limits.size>10000)for(const [k,v]of limits)if(v.until<now)limits.delete(k);}
async function body(req){let chunks=[],size=0;for await(const part of req){size+=part.length;if(size>32768)fail(413,'La solicitud es demasiado grande.');chunks.push(part);}try{const value=JSON.parse(Buffer.concat(chunks).toString()||'{}');if(!value||Array.isArray(value)||typeof value!=='object')fail(400,'Solicitud inválida.');return value;}catch{fail(400,'JSON inválido.');}}
function userFor(req){const token=(req.headers.authorization||'').replace(/^Bearer /,'');const u=one('SELECT u.* FROM sessions s JOIN users u ON s.user_id=u.id WHERE token_hash=? AND expires>?',tokenHash(token),Date.now());if(!u)fail(401,'Tu sesión expiró. Vuelve a iniciar sesión.');return u;}
function dateTime(day,time){if(typeof day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(day)||typeof time!=='string'||!/^\d{2}:00$/.test(time))fail(400,'Fecha u horario inválidos.');const d=new Date(`${day}T${time}:00`);if(!Number.isFinite(d.getTime())||localDay(d)!==day)fail(400,'Fecha inválida.');return d;}
function localDay(d){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
function bookingValues(b,u,existing){
 const service=integer(b.service,'Servicio',0,10000),barber=integer(b.barber,'Barbero',0,10000),cut=integer(b.cut??0,'Corte',0,5);
 const s=one('SELECT * FROM services WHERE id=?',service),br=one('SELECT * FROM barbers WHERE id=?',barber);
 if(!s?.active||!br?.active)fail(409,'El servicio o barbero ya no está disponible.');
 const d=dateTime(b.day,b.time),start=d.getTime(),config=hours();
 if(start<=Date.now()||start>Date.now()+181*86400000)fail(400,'Elige una fecha futura dentro de los próximos seis meses.');
 const preserve=existing?.service===service;
 const minutes=preserve?existing.service_minutes:s.minutes,price=preserve?existing.service_price:s.price,name=preserve?existing.service_name:s.name;
 const end=start+minutes*60000,closing=new Date(d);closing.setHours(config.closeHour,0,0,0);
 if(config.closedWeekdays.includes(d.getDay()+1)||d.getHours()<config.openHour||end>closing.getTime())fail(409,'La cita queda fuera del horario de atención.');
 if(one('SELECT id FROM appointments WHERE barber=? AND cancelled=0 AND id!=? AND start<? AND end>?',barber,existing?.id||'',end,start))fail(409,'Ese horario acaba de ocuparse. Selecciona otro.');
 const client=u.role==='owner'?str(b.client,'Nombre del cliente',2):u.name;
 const phone=u.role==='owner'?str(b.phone??'','Teléfono',0,30):existing?.phone||'';
 return {service,barber,cut,start,end,minutes,price,name,client,phone};
}
function ownedAppointment(id,u){const a=one('SELECT * FROM appointments WHERE id=?',id);if(!a)fail(404,'Cita no encontrada.');if(u.role!=='owner'&&a.user_id!==u.id)fail(403,'No puedes modificar esta cita.');return a;}

async function handle(req){
 const url=new URL(req.url,'http://localhost');const path=url.pathname;const method=req.method;
 if(path==='/health'&&method==='GET')return {status:'ok',service:'FreshCut',version:1};
 throttle(req.socket.remoteAddress,240);
 if(path==='/v1/catalog'&&method==='GET')return catalog();
 if(path==='/v1/auth/register'&&method==='POST'){
  throttle('auth:'+req.socket.remoteAddress,15);const b=await body(req);const email=emailValue(b.email),name=str(b.name,'Nombre',2),password=passwordValue(b.password);
  const hash=await hashPassword(password);
  return transaction(()=>{if(one('SELECT id FROM users WHERE email=?',email))fail(409,'Ese correo ya tiene una cuenta.');const uid=id();run("INSERT INTO users(id,name,email,password,role,created) VALUES(?,?,?,?,'client',?)",uid,name,email,hash,Date.now());return newSession(one('SELECT * FROM users WHERE id=?',uid));});
 }
 if(path==='/v1/auth/login'&&method==='POST'){
  throttle('auth:'+req.socket.remoteAddress,15);const b=await body(req),email=emailValue(b.email),password=passwordValue(b.password);const u=one('SELECT * FROM users WHERE email=?',email);
  if(!u){await hashPassword(password);fail(401,'Correo o contraseña incorrectos.');}
  if(!await verifyPassword(password,u.password))fail(401,'Correo o contraseña incorrectos.');
  if(b.owner===true&&u.role!=='owner')fail(403,'Esta cuenta no pertenece al dueño.');
  return newSession(u);
 }
 const u=userFor(req);
 if(path==='/v1/snapshot'&&method==='GET')return snapshot(u);
 if(path==='/v1/auth/logout'&&method==='POST'){run('DELETE FROM sessions WHERE token_hash=?',tokenHash((req.headers.authorization||'').replace(/^Bearer /,'')));return {ok:true};}
 if(path==='/v1/me'&&method==='PUT'){
  const b=await body(req);return transaction(()=>{const name=str(b.name,'Nombre',2),email=emailValue(b.email);if(one('SELECT id FROM users WHERE email=? AND id!=?',email,u.id))fail(409,'Ese correo ya está en uso.');run('UPDATE users SET name=?,email=? WHERE id=?',name,email,u.id);run('UPDATE appointments SET client=? WHERE user_id=?',name,u.id);return {ok:true};});
 }
 if(path==='/v1/me'&&method==='DELETE'){
  if(u.role==='owner')fail(409,'La cuenta del dueño se administra desde el servidor.');
  return transaction(()=>{run("UPDATE appointments SET cancelled=1,client='Cuenta eliminada',phone='' WHERE user_id=?",u.id);run('DELETE FROM posts WHERE user_id=?',u.id);run('DELETE FROM users WHERE id=?',u.id);return {ok:true};});
 }
 if(path==='/v1/preferences'&&method==='PUT'){const b=await body(req);run('UPDATE users SET reminders=? WHERE id=?',b.reminders===true?1:0,u.id);return {ok:true};}
 if(path==='/v1/availability'&&method==='GET'){
  const service=integer(Number(url.searchParams.get('service')),'Servicio',0,10000),barber=integer(Number(url.searchParams.get('barber')),'Barbero',0,10000);
  const except=url.searchParams.get('except');const old=except?ownedAppointment(except,u):undefined;
  const config=hours(),slots=[];
  for(let hour=config.openHour;hour<config.closeHour;hour++){
   const time=String(hour).padStart(2,'0')+':00';
   try{bookingValues({service,barber,day:url.searchParams.get('day'),time,cut:0,client:'Consulta de horario'},u,old);slots.push(time);}
   catch(e){if(!(e instanceof ApiError))throw e;}
  }
  return {slots};
 }
 if(path==='/v1/appointments'&&method==='POST'){
  const b=await body(req),aid=str(b.id,'Identificador',8,80);
  return transaction(()=>{const old=one('SELECT * FROM appointments WHERE id=?',aid);
   if(old){if(old.user_id!==u.id&&!(u.role==='owner'&&old.user_id===null))fail(409,'Identificador ya utilizado.');return {id:aid};}
   const v=bookingValues(b,u);
   run('INSERT INTO appointments(id,user_id,client,phone,service,barber,cut,start,end,service_name,service_minutes,service_price) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',aid,u.role==='owner'?null:u.id,v.client,v.phone,v.service,v.barber,v.cut,v.start,v.end,v.name,v.minutes,v.price);return {id:aid};});
 }
 const appt=path.match(/^\/v1\/appointments\/([^/]+)$/);
 if(appt&&method==='PUT'){
  const b=await body(req);return transaction(()=>{const a=ownedAppointment(appt[1],u);
   if(b.action==='cancel'){if(u.role!=='owner'&&a.start<Date.now())fail(409,'La cita ya pasó.');run('UPDATE appointments SET cancelled=1 WHERE id=?',a.id);}
   else if(b.action==='status'){owner(u);if(!['','done','noshow'].includes(b.status))fail(400,'Estado inválido.');if(a.cancelled)fail(409,'La cita está cancelada.');run('UPDATE appointments SET status=? WHERE id=?',b.status,a.id);}
   else {if(a.cancelled||a.status||a.start<Date.now())fail(409,'Solo se pueden reprogramar citas pendientes futuras.');const v=bookingValues(b,u,a);run('UPDATE appointments SET service=?,barber=?,cut=?,start=?,end=?,service_name=?,service_minutes=?,service_price=?,client=?,phone=? WHERE id=?',v.service,v.barber,v.cut,v.start,v.end,v.name,v.minutes,v.price,v.client,v.phone,a.id);}
   return {ok:true};});
 }
 const cat=path.match(/^\/v1\/owner\/(services|barbers)\/(\d+)$/);
 if(cat&&method==='PUT'){
  owner(u);const b=await body(req),table=cat[1],index=Number(cat[2]);return transaction(()=>{
   const count=one(`SELECT count(*) n FROM ${table}`).n;if(index>count)fail(400,'Identificador inválido.');
   const active=b.active===true?1:0;if(!active&&one(`SELECT id FROM ${table} WHERE id=? AND active=1`,index)&&one(`SELECT count(*) n FROM ${table} WHERE active=1`).n<=1)fail(409,'Debe quedar al menos uno activo.');
   const name=str(b.name,'Nombre',2),photo=integer(b.photo,'Foto',0,6);
   if(table==='services')run('INSERT INTO services VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,minutes=excluded.minutes,price=excluded.price,photo=excluded.photo,active=excluded.active',index,name,integer(b.minutes,'Duración',5,240),integer(b.price,'Precio',0,100000),photo,active);
   else run('INSERT INTO barbers VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,specialty=excluded.specialty,photo=excluded.photo,active=excluded.active',index,name,str(b.specialty,'Especialidad',2),photo,'Nuevo',active);
   return {ok:true};});
 }
 if(path==='/v1/owner/hours'&&method==='PUT'){
  owner(u);const b=await body(req);const open=integer(b.openHour,'Apertura',0,23),close=integer(b.closeHour,'Cierre',1,24);if(open>=close||!Array.isArray(b.closedWeekdays)||b.closedWeekdays.length>6)fail(400,'Horario inválido.');const closed=[...new Set(b.closedWeekdays.map(d=>integer(d,'Día',1,7)))];run('UPDATE settings SET open_hour=?,close_hour=?,closed=? WHERE id=1',open,close,JSON.stringify(closed));return {ok:true};
 }
 if(path==='/v1/owner/demo'&&method==='DELETE'){owner(u);run('DELETE FROM appointments WHERE demo=1');return {ok:true};}
 if(path==='/v1/owner/demo'&&method==='POST'){
  owner(u);return transaction(()=>{for(let n=1;n<=4;n++){const day=new Date();day.setDate(day.getDate()+n);const s=one('SELECT * FROM services WHERE active=1 LIMIT 1'),b=one('SELECT * FROM barbers WHERE active=1 LIMIT 1');try{const v=bookingValues({service:s.id,barber:b.id,cut:n%6,day:localDay(day),time:String(hours().openHour).padStart(2,'0')+':00',client:'Cliente de ejemplo '+n,phone:''},u);run('INSERT INTO appointments(id,user_id,client,phone,service,barber,cut,start,end,service_name,service_minutes,service_price,demo) VALUES(?,NULL,?,?,?,?,?,?,?,?,?,?,1)',`demo-${localDay(day)}`,v.client,'',v.service,v.barber,v.cut,v.start,v.end,v.name,v.minutes,v.price);}catch(e){if(!(e instanceof ApiError)&&!String(e.message).includes('UNIQUE'))throw e;}}return {ok:true};});
 }
 if(path==='/v1/favorites'&&method==='PUT'){const b=await body(req),cut=integer(b.cut,'Corte',0,5);if(b.active===true)run('INSERT OR IGNORE INTO favorites VALUES(?,?)',u.id,cut);else run('DELETE FROM favorites WHERE user_id=? AND cut=?',u.id,cut);return {ok:true};}
 if(path==='/v1/posts'&&method==='POST'){const b=await body(req);run('INSERT INTO posts(user_id,author,cut,body,created) VALUES(?,?,?,?,?)',u.id,u.name,integer(b.cut??0,'Corte',0,5),str(b.body,'Publicación',1,500),Date.now());return {ok:true};}
 // Feed routes are installed by the recommendation module in phase 2.
 if(handleFeed){const result=await handleFeed({path,method,url,req,u,body,fail,integer,str});if(result!==undefined)return result;}
 fail(404,'Ruta no encontrada.');
}
const server=http.createServer(async(req,res)=>{
 try{const result=await handle(req);res.writeHead(200,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(result));}
 catch(e){const status=e.status||500;if(status===500)console.error('Error de servidor:',e.message);res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify({error:status===500?'No se pudo completar la operación.':e.message}));}
});
server.requestTimeout=15000;server.headersTimeout=10000;
server.listen(port,host,()=>console.log(`FreshCut backend disponible en http://${host}:${port} · zona America/Mazatlan`));
function shutdown(){server.close(()=>{db.close();process.exit(0);});setTimeout(()=>process.exit(1),5000).unref();}
process.on('SIGINT',shutdown);process.on('SIGTERM',shutdown);
