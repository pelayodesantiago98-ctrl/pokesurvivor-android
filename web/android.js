/* ============ android.js — PokéSurvivor como app de Android, un jugador ============
 * Se carga después de game.js y antes de main.js (lo inserta build.ps1).
 * - Sin nube: firebase-config.js va a null, así G.DB arranca en modo local
 *   y la partida se guarda en el almacenamiento de la app.
 * - Sin cuenta ni contraseña: la primera vez sólo se pide el nombre; luego
 *   se entra directamente.
 * - Fuera lo que necesita internet: amigos/cooperativo, ranking, apoyar,
 *   actualizador, cerrar sesión.
 */
(() => {
  const PASS = 'android-local';           // contraseña fija de la cuenta local (nunca se ve)
  const $ = id => document.getElementById(id);

  // ---- Pantalla de inicio: sólo el nombre ----
  const baseInit = G.LoginUI.init;
  G.LoginUI.init = function () {
    baseInit.call(this);
    $('login-form').onsubmit = async e => {
      e.preventDefault();
      $('login-err').textContent = '';
      $('btn-auth').disabled = true;
      const name = $('f-user').value;
      // Si ya existe una cuenta con ese nombre (reinstalación con datos), se entra en ella.
      let r = await G.DB.login(name, PASS);
      if (!r.ok) r = await G.DB.register(name, PASS);
      $('btn-auth').disabled = false;
      if (!r.ok) { $('login-err').textContent = r.error; return; }
      G.Flow.afterLogin();
    };
  };
  const baseOpen = G.LoginUI.open;
  G.LoginUI.open = function () {
    baseOpen.call(this);
    $('btn-auth').textContent = 'Empezar';
    $('login-mode').textContent = 'Tu partida se guarda en este móvil.';
    $('login-err').textContent = '';
  };
  G.LoginUI.offerRecovery = () => {};

  // ---- Sin actualizador (la app se actualiza instalando un APK nuevo) ----
  G.Updater.init = () => {};
})();
