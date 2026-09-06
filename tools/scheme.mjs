import * as m from '@material/material-color-utilities';

const seed = m.Hct.fromInt(m.argbFromHex('#0B57D0'));
const names = [
  'primary', 'onPrimary', 'primaryContainer', 'onPrimaryContainer',
  'secondary', 'onSecondary', 'secondaryContainer', 'onSecondaryContainer',
  'tertiary', 'onTertiary', 'tertiaryContainer', 'onTertiaryContainer',
  'error', 'onError', 'errorContainer', 'onErrorContainer',
  'background', 'onBackground', 'surface', 'onSurface', 'surfaceVariant', 'onSurfaceVariant',
  'outline', 'outlineVariant', 'scrim', 'inverseSurface', 'inverseOnSurface', 'inversePrimary',
  'surfaceDim', 'surfaceBright', 'surfaceContainerLowest', 'surfaceContainerLow',
  'surfaceContainer', 'surfaceContainerHigh', 'surfaceContainerHighest',
];

const lines = [
  'package eu.hxreborn.pixelinjector.ui.theme',
  '',
  'import androidx.compose.ui.graphics.Color',
];
for (const dark of [false, true]) {
  const scheme = new m.SchemeTonalSpot(seed, dark, 0, '2025', 'phone');
  const suffix = dark ? 'Dark' : 'Light';
  for (const n of names) {
    const hex = m.hexFromArgb(scheme[n]).toUpperCase().slice(1);
    lines.push(`val ${n}${suffix} = Color(0xFF${hex})`);
  }
  lines.push('');
}
process.stdout.write(lines.join('\n'));
