/** Codici meteo WMO usati da Open-Meteo (weather_code). */
const CODES: Record<number, [string, string]> = {
  0: ['Sereno', 'clear_day'],
  1: ['Prevalentemente sereno', 'partly_cloudy_day'],
  2: ['Parzialmente nuvoloso', 'partly_cloudy_day'],
  3: ['Coperto', 'cloud'],
  45: ['Nebbia', 'foggy'],
  48: ['Nebbia con brina', 'foggy'],
  51: ['Pioviggine leggera', 'rainy_light'],
  53: ['Pioviggine', 'rainy_light'],
  55: ['Pioviggine intensa', 'rainy'],
  56: ['Pioviggine gelata', 'weather_mix'],
  57: ['Pioviggine gelata intensa', 'weather_mix'],
  61: ['Pioggia debole', 'rainy_light'],
  63: ['Pioggia', 'rainy'],
  65: ['Pioggia forte', 'rainy_heavy'],
  66: ['Pioggia gelata', 'weather_mix'],
  67: ['Pioggia gelata forte', 'weather_mix'],
  71: ['Neve debole', 'weather_snowy'],
  73: ['Neve', 'weather_snowy'],
  75: ['Neve forte', 'snowing_heavy'],
  77: ['Granelli di neve', 'weather_snowy'],
  80: ['Rovesci deboli', 'rainy_light'],
  81: ['Rovesci', 'rainy'],
  82: ['Rovesci violenti', 'rainy_heavy'],
  85: ['Rovesci di neve', 'weather_snowy'],
  86: ['Rovesci di neve forti', 'snowing_heavy'],
  95: ['Temporale', 'thunderstorm'],
  96: ['Temporale con grandine', 'thunderstorm'],
  99: ['Temporale con grandine forte', 'thunderstorm'],
};

export function weatherLabel(code: number | null | undefined): string {
  return code === null || code === undefined ? '—' : CODES[code]?.[0] ?? `Codice ${code}`;
}

export function weatherIcon(code: number | null | undefined, isDay = true): string {
  const icon = code === null || code === undefined ? 'help' : CODES[code]?.[1] ?? 'help';
  if (!isDay) {
    if (icon === 'clear_day') return 'bedtime';
    if (icon === 'partly_cloudy_day') return 'partly_cloudy_night';
  }
  return icon;
}
