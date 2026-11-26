import { db, one, run, id, hashPassword, dataDir } from './db.mjs';
import { randomBytes } from 'node:crypto';
import { writeFileSync } from 'node:fs';
const [command, emailArg] = process.argv.slice(2);
const email=(emailArg||'dueno@freshcut.local').trim().toLowerCase();
if (!['init-owner','reset-password'].includes(command) || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
 console.error('Uso: npm run admin -- init-owner [correo] | reset-password correo'); process.exitCode=1;
} else {
 const existing=one('SELECT * FROM users WHERE email=?',email);
 if (command==='init-owner' && one("SELECT id FROM users WHERE role='owner'")) console.log('La cuenta del dueño ya existe. No se modificó.');
 else if(command==='reset-password' && !existing) {console.error('No existe esa cuenta.');process.exitCode=1;}
 else {
  const password=randomBytes(15).toString('base64url'); const hash=await hashPassword(password);
  if(command==='init-owner') {
   if(existing) throw new Error('El correo ya pertenece a un cliente. Usa otro correo.');
   run("INSERT INTO users(id,name,email,password,role,created) VALUES(?,?,?,?,'owner',?)",id(),'Dueño FreshCut',email,hash,Date.now());
  } else {run('UPDATE users SET password=? WHERE id=?',hash,existing.id);run('DELETE FROM sessions WHERE user_id=?',existing.id);}
  const path=dataDir+'owner-access.txt';
  writeFileSync(path,`Acceso local FreshCut\nCorreo: ${email}\nContraseña: ${password}\n\nNo compartir ni publicar este archivo.\n`,{mode:0o600});
  console.log(`Credenciales guardadas en ${path}. No se imprimen en el registro.`);
 }
}
db.close();
