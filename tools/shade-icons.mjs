import { readFileSync, writeFileSync } from "node:fs";

const names = `
notifications_off notifications_paused notifications notifications_active snooze do_not_disturb_on do_not_disturb_off block
volume_off volume_mute vibration bedtime bedtime_off dark_mode light_mode sunny nightlight cloud cloud_off air water_drop
bolt local_fire_department eco park forest grass local_florist pets cruelty_free emoji_nature flutter_dash landscape
filter_drama rainy ac_unit thermostat star kid_star auto_awesome celebration cake emoji_events trophy workspace_premium
military_tech diamond favorite thumb_up thumb_down sentiment_satisfied sentiment_very_satisfied sentiment_neutral
sentiment_dissatisfied sentiment_content sentiment_calm mood mood_bad self_improvement spa psychiatry face smart_toy
sports_esports rocket rocket_launch flight directions_bike directions_run coffee local_cafe icecream lunch_dining
music_note headphones mic_off piano palette brush draw menu_book auto_stories school science biotech lightbulb
tips_and_updates bug_report terminal code memory lock lock_open key shield verified_user check check_circle task_alt
done_all close cancel remove inbox drafts mark_email_read mail send mark_chat_read chat_bubble forum sms call public
language explore map pin_drop home house cottage weekend bed fitness_center sports_soccer sports_basketball surfing
anchor sailing two_wheeler directions_car train hiking kayaking skateboarding bakery_dining ramen_dining local_pizza
emoji_food_beverage grade hourglass_empty hourglass_top schedule timer alarm bedroom_baby cloudy_snowing
`.split(/\s+/).filter(Boolean);

const [, , packageDir, out] = process.argv;
const skipped = [];
const rows = [];
for (const name of new Set(names)) {
  let svg;
  try {
    svg = readFileSync(`${packageDir}/outlined/${name}.svg`, "utf8");
  } catch {
    skipped.push(name);
    continue;
  }
  const paths = [...svg.matchAll(/ d="([^"]+)"/g)].map((m) => m[1]);
  if (paths.length !== 1 || !svg.includes('viewBox="0 -960 960 960"')) {
    skipped.push(name);
    continue;
  }
  rows.push(`${name}\t${paths[0]}`);
}
writeFileSync(out, rows.join("\n") + "\n");
console.log(`wrote ${rows.length} icons, skipped ${skipped.length}: ${skipped.join(" ")}`);
