# Where Winds Blow Datapack Notes

Foliage sway selection is client-resource driven rather than datapack driven so it also works when the mod is installed client-side on an unmodded server. See [FOLIAGE_PROFILES.md](FOLIAGE_PROFILES.md) for block IDs, block tags, motion types, interaction settings, and opt-outs.

`Where Winds Blow` exposes biome-specific grass placement through datapack JSON, so pack creators can control where each grass family spawns without editing code.

## Canonical biome tags

These are the tags pack creators should target going forward:

- `#where_winds_blow:has_dense_short_grass`
- `#where_winds_blow:has_medium_short_grass`
- `#where_winds_blow:has_sparse_short_grass`
- `#where_winds_blow:has_tall_grass`
- `#where_winds_blow:has_fern_accents`
- `#where_winds_blow:has_overgrown_grass`
- `#where_winds_blow:has_overgrown_grass_fields`
- `#where_winds_blow:has_short_dry_grass`
- `#where_winds_blow:has_tall_dry_grass`
- `#where_winds_blow:has_dead_grass`
- `#where_winds_blow:has_wild_wheat`

Legacy tag names still exist as aliases for compatibility, but new datapacks should prefer the canonical names above.

## File layout

Biome membership lives in:

- `data/where_winds_blow/tags/worldgen/biome/*.json`

Biome modifiers that attach the placed features live in:

- `data/where_winds_blow/neoforge/biome_modifier/*.json`

Configured and placed feature definitions live in:

- `data/where_winds_blow/worldgen/configured_feature/*.json`
- `data/where_winds_blow/worldgen/placed_feature/*.json`

Surface rules for dry and dead grasses live in:

- `data/where_winds_blow/tags/block/dry_grass_plantable_on.json`

## Grass type mapping

- Dense short grass: `has_dense_short_grass` -> `where_winds_blow_dense_grass.json`
- Medium short grass: `has_medium_short_grass` -> `where_winds_blow_medium_grass.json`
- Sparse short grass: `has_sparse_short_grass` -> `where_winds_blow_sparse_grass.json`
- Tall grass: `has_tall_grass` -> `where_winds_blow_tall_grass.json`
- Fern accents: `has_fern_accents` -> `where_winds_blow_fern_accents.json`
- Overgrown grass: `has_overgrown_grass` -> `where_winds_blow_overgrown_grass.json`
- Overgrown grass fields: `has_overgrown_grass_fields` -> `where_winds_blow_overgrown_grass_fields.json`
- Short dry grass: `has_short_dry_grass` -> `where_winds_blow_short_dry_grass.json`
- Tall dry grass: `has_tall_dry_grass` -> `where_winds_blow_tall_dry_grass.json`
- Dead grass: `has_dead_grass` -> `where_winds_blow_dead_grass.json`
- Wild wheat: `has_wild_wheat` -> `where_winds_blow_wild_wheat.json`

## Mixed-variant notes

- Tall-grass and fern-accent placement uses terrain noise so plants gather into broad, coherent regions instead of evenly scattered spots.
- Tall-grass patches now form irregular clustered interiors with a short-grass fringe. Overgrown patches step down from height-graded cores through tall grass into mixed short grass.
- Fern accents place the vanilla `fern` block, the feature changes composition without replacing its model or texture.
- Dense, medium, and sparse short-grass patches can mix vanilla `short_grass` with `flat_grass`. That mix is controlled in the configured feature JSON, not by separate biome tags.
- Dead-grass patches mix `short_dead_grass` and `flat_dead_grass` inside `dead_grass_patch.json`.
- Dry and dead grasses use `dry_grass_plantable_on.json` for survival, so a datapack can expand or restrict valid ground blocks there.

## Typical overrides

To move a grass family to different biomes:

1. Override the matching canonical biome tag JSON in your datapack.
2. Add or remove biome IDs from the `values` list.

To change patch shape or density:

1. Override the relevant file in `configured_feature/` to change patch shape.
2. Override the relevant file in `placed_feature/` to change pass count or rarity.

To change which block variants appear inside one patch:

1. Override the relevant configured feature.
2. Edit the `to_place` state provider.

## Example

Example override path for dead grass:

```text
data/where_winds_blow/tags/worldgen/biome/has_dead_grass.json
```

Example contents:

```json
{
  "replace": false,
  "values": [
    "minecraft:savanna",
    "minecraft:savanna_plateau",
    "minecraft:windswept_savanna",
    "minecraft:badlands"
  ]
}
```

## Biome wind profiles

Dynamic wind profiles live at:

```text
data/<namespace>/where_winds_blow/wind_profiles/<profile>.json
```

The `biomes` field accepts one biome ID, one biome tag prefixed with `#`, or an array containing both. Use `"*"` for a fallback. Higher priorities win; ties are resolved by the profile resource ID so pack order remains deterministic.

```json
{
  "biomes": [
    "minecraft:meadow",
    "#minecraft:is_mountain"
  ],
  "priority": 10,
  "base_strength_multiplier": 1.2,
  "gust_strength_multiplier": 1.35,
  "gust_frequency_multiplier": 1.25,
  "turbulence_multiplier": 1.3,
  "direction_instability_multiplier": 1.15,
  "altitude_influence": 0.8
}
```

All multiplier fields are optional and default to `1.0`. Strength, gust, frequency, turbulence, and direction multipliers accept values from `0.0` to `4.0`; `altitude_influence` accepts `0.0` to `2.0`; and priority accepts `-10000` to `10000`. Invalid profile files are logged and skipped without stopping the reload.

The mod ships fallback, plains, forest, mountain, desert/badlands, snowy, ocean/coast, and swamp profiles. Integrated worlds use server datapack profiles. The same defaults are mirrored into client resources so client-side visual wind remains available on servers that do not require the mod.
