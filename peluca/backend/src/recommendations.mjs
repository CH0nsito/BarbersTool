import { all, one, run, transaction, cutCategories } from './db.mjs';
import { createHash, randomUUID } from 'node:crypto';

const DAY=86400000;
/** Content-based ranking. Explicit preferences + decayed implicit signals, with variety. */
export function rankFeed(userId,sort){
 const now=Date.now();
 const posts=all(`SELECT p.*, (SELECT count(*) FROM reactions WHERE post_id=p.id AND kind='like') likes,
 (SELECT count(*) FROM reactions WHERE post_id=p.id AND kind='save') saves,
 (SELECT count(*) FROM comments WHERE post_id=p.id) comments FROM posts p ORDER BY created DESC LIMIT 200`);
 const reactions=all('SELECT * FROM reactions WHERE user_id=?',userId);
 const hidden=new Set(reactions.filter(r=>r.kind==='hide').map(r=>r.post_id));
 const saved=new Set(reactions.filter(r=>r.kind==='save').map(r=>r.post_id));
 const affinity=Object.fromEntries(cutCategories.map(c=>[c,0]));
 const add=(cut,value)=>{const c=cutCategories[cut];if(c)affinity[c]+=value;};
 for(const f of all('SELECT cut FROM favorites WHERE user_id=?',userId))add(f.cut,4);
 for(const r of reactions){const p=one('SELECT cut FROM posts WHERE id=?',r.post_id);if(p)add(p.cut,r.kind==='like'?3:r.kind==='save'?5:-3);}
 for(const a of all('SELECT cut FROM appointments WHERE user_id=? AND cancelled=0 AND start>? ORDER BY start DESC LIMIT 60',userId,now-90*DAY))add(a.cut,6);
 const exposures=new Map();
 for(const e of all('SELECT * FROM events WHERE user_id=? AND created>? ORDER BY created DESC LIMIT 2000',userId,now-90*DAY)){
  const decay=Math.exp(-(now-e.created)/(21*DAY));
  const weight=e.kind==='open'?1.5:e.kind==='view'?Math.min(e.value,30)/15:e.kind==='skip'?-.4:0;
  add(e.cut,weight*decay);
  if(e.post_id&&e.kind==='view')exposures.set(e.post_id,(exposures.get(e.post_id)||0)+decay);
 }
 const candidates=posts.filter(p=>sort==='saved'?saved.has(p.id):!hidden.has(p.id)).map(p=>{
  const category=cutCategories[p.cut],interest=Math.min(20,Math.max(-10,affinity[category]||0));
  const popularity=Math.log1p(p.likes*2+p.saves*3+p.comments)*1.2;
  const freshness=3*Math.exp(-Math.max(0,now-p.created)/(7*DAY));
  // Stable daily exploration: no random reorder on every UI recomposition.
  const exploration=createHash('sha256').update(`${userId}:${p.id}:${Math.floor(now/DAY)}`).digest()[0]/255;
  const seen=exposures.get(p.id)||0;
  return {...p,category,score:interest+popularity+freshness+exploration*1.5-Math.min(4,seen),reason:interest>1?'Por tu interés en '+category:seen<1?'Descubre un estilo diferente':'Novedades de la barbería'};
 });
 let ordered;
 if(sort==='recent'||sort==='saved')ordered=candidates.sort((a,b)=>b.created-a.created||b.id-a.id);
 else if(sort==='popular')ordered=candidates.sort((a,b)=>b.likes-a.likes||b.saves-a.saves||b.created-a.created);
 else {
  ordered=[];const used={};
  while(candidates.length&&ordered.length<50){
   candidates.sort((a,b)=>(b.score-(used[b.category]||0)*2)-(a.score-(used[a.category]||0)*2)||b.id-a.id);
   const pick=candidates.shift();ordered.push(pick);used[pick.category]=(used[pick.category]||0)+1;
  }
 }
 return {posts:ordered.slice(0,50).map(p=>({id:p.id,cut:p.cut,author:p.author,body:p.body,created:p.created,likes:p.likes,comments:p.comments,reason:sort==='personal'?p.reason:sort==='saved'?'Guardado por ti':sort==='recent'?'Publicado recientemente':'Popular en FreshCut'}))};
}
export async function handleFeed({path,method,url,req,u,body,fail,integer,str}){
 if(path==='/v1/feed'&&method==='GET'){
  const sort=url.searchParams.get('sort')||'personal';if(!['personal','recent','popular','saved'].includes(sort))fail(400,'Orden inválido.');
  return rankFeed(u.id,sort);
 }
 if(path==='/v1/reactions'&&method==='PUT'){
  const b=await body(req),post=integer(b.post,'Publicación',1,2147483647);
  if(!['like','save','hide'].includes(b.kind)||typeof b.active!=='boolean')fail(400,'Reacción inválida.');
  if(!one('SELECT id FROM posts WHERE id=?',post))fail(404,'Publicación no encontrada.');
  if(b.active)run('INSERT INTO reactions VALUES(?,?,?,?) ON CONFLICT(user_id,post_id,kind) DO NOTHING',u.id,post,b.kind,Date.now());
  else run('DELETE FROM reactions WHERE user_id=? AND post_id=? AND kind=?',u.id,post,b.kind);
  return {ok:true};
 }
 if(path==='/v1/comments'&&method==='POST'){
  const b=await body(req),post=integer(b.post,'Publicación',1,2147483647);
  if(!one('SELECT id FROM posts WHERE id=?',post))fail(404,'Publicación no encontrada.');
  const text=str(b.body,'Comentario',1,500);
  run('INSERT INTO comments VALUES(?,?,?,?,?)',randomUUID(),u.id,post,text,Date.now());return {ok:true};
 }
 if(path==='/v1/comments'&&method==='GET'){
  const post=integer(Number(url.searchParams.get('post')),'Publicación',1,2147483647);
  return {comments:all('SELECT c.id,c.body,c.created,u.name author FROM comments c JOIN users u ON c.user_id=u.id WHERE post_id=? ORDER BY created DESC LIMIT 50',post)};
 }
 if(path==='/v1/events'&&method==='POST'){
  const b=await body(req),eid=str(b.id,'Identificador',8,80),kind=b.kind;
  if(!['view','open','skip'].includes(kind))fail(400,'Señal inválida.');
  const post=b.post==null?null:integer(b.post,'Publicación',1,2147483647);
  let cut=integer(b.cut,'Corte',0,5);
  if(post!==null){const p=one('SELECT cut FROM posts WHERE id=?',post);if(!p)fail(404,'Publicación no encontrada.');cut=p.cut;}
  if(kind!=='open'&&post===null)fail(400,'La exposición requiere una publicación.');
  const value=kind==='view'?Math.min(60,Math.max(0,Number(b.value))):1;
  if(!Number.isFinite(value)||kind==='view'&&value<2)fail(400,'Exposición inválida.');
  const now=Date.now();
  // Cap repeated exposures per item/day to avoid a single post dominating the profile.
  if(one('SELECT count(*) n FROM events WHERE user_id=? AND cut=? AND kind=? AND created>?',u.id,cut,kind,now-DAY).n<20)
   run('INSERT OR IGNORE INTO events VALUES(?,?,?,?,?,?,?)',eid,u.id,post,cut,kind,value,now);
  run('DELETE FROM events WHERE created<?',now-90*DAY);
  return {ok:true};
 }
 if(path==='/v1/recommendations/history'&&method==='DELETE')return transaction(()=>{
  run('DELETE FROM events WHERE user_id=?',u.id);run("DELETE FROM reactions WHERE user_id=? AND kind='hide'",u.id);return {ok:true};
 });
 return undefined;
}
