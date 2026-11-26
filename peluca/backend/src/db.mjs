import { DatabaseSync } from 'node:sqlite';
import { mkdirSync, chmodSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { randomBytes, scrypt as scryptCb, timingSafeEqual, createHash, randomUUID } from 'node:crypto';
import { promisify } from 'node:util';

// Business-local wall clock; all API dates use YYYY-MM-DD in this zone.
process.env.TZ = 'America/Mazatlan';
export const dataDir = fileURLToPath(new URL('../data/', import.meta.url));
mkdirSync(dataDir, { recursive: true, mode: 0o700 });
chmodSync(dataDir, 0o700);
export const db = new DatabaseSync(dataDir + 'freshcut.sqlite');
chmodSync(dataDir + 'freshcut.sqlite', 0o600);
db.exec(`PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
 CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('client','owner')), reminders INTEGER NOT NULL DEFAULT 1, created INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,expires INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS services(id INTEGER PRIMARY KEY,name TEXT NOT NULL,minutes INTEGER NOT NULL,price INTEGER NOT NULL,photo INTEGER NOT NULL,active INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS barbers(id INTEGER PRIMARY KEY,name TEXT NOT NULL,specialty TEXT NOT NULL,photo INTEGER NOT NULL,rating TEXT NOT NULL,active INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS settings(id INTEGER PRIMARY KEY CHECK(id=1),open_hour INTEGER NOT NULL,close_hour INTEGER NOT NULL,closed TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS appointments(id TEXT PRIMARY KEY,user_id TEXT REFERENCES users(id) ON DELETE SET NULL,client TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',service INTEGER NOT NULL REFERENCES services(id),barber INTEGER NOT NULL REFERENCES barbers(id),cut INTEGER NOT NULL,start INTEGER NOT NULL,end INTEGER NOT NULL,service_name TEXT NOT NULL,service_minutes INTEGER NOT NULL,service_price INTEGER NOT NULL,cancelled INTEGER NOT NULL DEFAULT 0,status TEXT NOT NULL DEFAULT '',demo INTEGER NOT NULL DEFAULT 0);
 CREATE INDEX IF NOT EXISTS appointment_overlap ON appointments(barber,start,end);
 CREATE TABLE IF NOT EXISTS favorites(user_id TEXT REFERENCES users(id) ON DELETE CASCADE,cut INTEGER NOT NULL,PRIMARY KEY(user_id,cut));
 CREATE TABLE IF NOT EXISTS posts(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id TEXT REFERENCES users(id) ON DELETE SET NULL,author TEXT NOT NULL,cut INTEGER NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL,seed INTEGER NOT NULL DEFAULT 0);
 CREATE TABLE IF NOT EXISTS reactions(user_id TEXT REFERENCES users(id) ON DELETE CASCADE,post_id INTEGER REFERENCES posts(id) ON DELETE CASCADE,kind TEXT NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(user_id,post_id,kind));
 CREATE TABLE IF NOT EXISTS comments(id TEXT PRIMARY KEY,user_id TEXT REFERENCES users(id) ON DELETE CASCADE,post_id INTEGER REFERENCES posts(id) ON DELETE CASCADE,body TEXT NOT NULL,created INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY,user_id TEXT REFERENCES users(id) ON DELETE CASCADE,post_id INTEGER REFERENCES posts(id) ON DELETE CASCADE,cut INTEGER NOT NULL,kind TEXT NOT NULL,value REAL NOT NULL,created INTEGER NOT NULL);
 CREATE INDEX IF NOT EXISTS events_by_user ON events(user_id,created);
`);
export const all = (sql, ...args) => db.prepare(sql).all(...args);
export const one = (sql, ...args) => db.prepare(sql).get(...args);
export const run = (sql, ...args) => db.prepare(sql).run(...args);
export const transaction = fn => { db.exec('BEGIN IMMEDIATE'); try { const result = fn(); db.exec('COMMIT'); return result; } catch (e) { db.exec('ROLLBACK'); throw e; } };
export const cutCategories = ['Fade', 'Clásicos', 'Clásicos', 'Modernos', 'Fade', 'Modernos'];
export const cutNames = ['Fade Medio', 'Taper Classic', 'Pompadour', 'French Crop', 'Curly Fade', 'Buzz Cut'];
transaction(() => {
 if (!one('SELECT id FROM services LIMIT 1')) {
  [['Corte de cabello',30,150,0],['Corte + barba',45,220,1],['Arreglo de barba',20,100,6],['Diseño de ceja',15,50,2]].forEach((s,i)=>run('INSERT INTO services VALUES(?,?,?,?,?,1)',i,...s));
 }
 if (!one('SELECT id FROM barbers LIMIT 1')) {
  [['Jason Miller','Especialista en fades',6,'4.9'],['Carlos Ramírez','Clásicos y barba',2,'4.8'],['Diego Torres','Textura y estilo urbano',4,'4.9']].forEach((b,i)=>run('INSERT INTO barbers VALUES(?,?,?,?,?,1)',i,...b));
 }
 run('INSERT OR IGNORE INTO settings VALUES(1,10,19,?)', '[]');
 if (!one('SELECT id FROM posts LIMIT 1')) cutNames.forEach((name,i)=>run('INSERT INTO posts(user_id,author,cut,body,created,seed) VALUES(NULL,?,?,?,?,1)', 'FreshCut Barbería',i,`${name}: inspiración para tu próxima visita. #${cutCategories[i]}`,Date.now()-(6-i)*86400000));
});
const scrypt = promisify(scryptCb);
export async function hashPassword(password) { const salt=randomBytes(16).toString('hex'); const key=await scrypt(password,salt,64); return `scrypt:${salt}:${key.toString('hex')}`; }
export async function verifyPassword(password, stored) { const [,salt,hash]=stored.split(':'); const key=await scrypt(password,salt,64); const expected=Buffer.from(hash,'hex'); return expected.length===key.length && timingSafeEqual(key,expected); }
export const tokenHash = token => createHash('sha256').update(token).digest('hex');
export function newSession(user) { const token=randomBytes(32).toString('hex'); run('DELETE FROM sessions WHERE expires < ?',Date.now()); run('INSERT INTO sessions VALUES(?,?,?)',tokenHash(token),user.id,Date.now()+30*86400000); return {token, user: publicUser(user)}; }
export function publicUser(u) { return {id:u.id,name:u.name,email:u.email,role:u.role,reminders:!!u.reminders}; }
export const id = () => randomUUID();
export function appointmentJSON(a) {
 const d=new Date(a.start); d.setHours(0,0,0,0);
 return {id:a.id,service:a.service,date:d.getTime(),time:new Date(a.start).toTimeString().slice(0,5),barber:a.barber,cut:a.cut,cancelled:!!a.cancelled,client:a.client,phone:a.phone,clientEmail:a.email||'',clientId:a.user_id||'',status:a.status,serviceName:a.service_name,serviceMinutes:a.service_minutes,servicePrice:a.service_price,demo:!!a.demo};
}
