// Proxy di "ng serve": inoltra le API ai servizi.
// Default: i container sull'host (porte 10080-10082). Nel container di sviluppo le variabili d'ambiente
// puntano ai nomi dei servizi Docker (http://inverter:8080, ...).
const to = (variable, fallback) => ({ target: process.env[variable] ?? fallback, secure: false, changeOrigin: true });

const inverter = to('INVERTER_URL', 'http://localhost:10080');
const openmeteo = to('OPENMETEO_URL', 'http://localhost:10081');
const advisor = to('ADVISOR_URL', 'http://localhost:10082');

export default {
  '/api/inverter': inverter,
  '/api/meteo': openmeteo,
  '/api/advice': advisor,
  '/api/rules': advisor,
  '/api/settings': advisor,
  '/api/history': advisor,
};
