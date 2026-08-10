# Foliage sway profiles

Where Winds Blow uses client resource JSON to decide exactly which blocks sway and how they behave. It does not infer foliage from Java classes, registry names, or model shape.

Profiles belong at:

```text
assets/<namespace>/where_winds_blow/foliage_profiles/<profile>.json
```

They can be supplied by a mod or a client resource pack. Reload resources with `F3 + T` after editing a profile.

## Example

```json
{
  "type": "foliage",
  "priority": 100,
  "interactive": true,
  "sway_start_height_multiplier": 0.8,
  "sway_strength_multiplier": 0.9,
  "interaction_strength_multiplier": 1.0,
  "blocks": [
    "examplemod:blue_flower",
    "#examplemod:soft_shrubs"
  ]
}
```

`blocks` accepts one selector or an array. A selector is either a block ID or a block tag prefixed with `#`. Explicit block IDs work in the mod's client-only mode. Tag selectors use the block tags supplied by the current world or server.

## Motion types

| Type | Anchor and motion | Typical use |
| --- | --- | --- |
| `grass` | Bottom-rooted, single block | Grass, ferns, small ground cover |
| `foliage` | Bottom-rooted, single block | Flowers, saplings, decorative foliage |
| `crop` | Bottom-rooted, single block | Crops and stems |
| `tall_foliage` | Bottom-rooted, profile-wide vertical column | Tall grass, reeds, upward vines |
| `hanging_foliage` | Top-rooted, profile-wide vertical column | Hanging roots and downward vines |
| `leaves` | Distributed movement across leaf-block geometry | Tree leaves |
| `none` | No sway or interaction | Explicit opt-out override |

Blocks in the same `tall_foliage` or `hanging_foliage` profile are treated as one continuous column. Put related head and body blocks in the same file.

## Fields

- `type` is required and must be one of the types above.
- `blocks` is required and contains block IDs and/or block tags.
- `priority` defaults to `0`. Higher-priority matching profiles win. Equal priorities are resolved by profile resource ID for deterministic results.
- `interactive` defaults to `true` for plant types and `false` for `leaves` and `none`.
- `sway_start_height_multiplier` scales the global plant sway-start setting. Lower values let motion begin closer to the anchor. It defaults to `1.0`.
- `height_scale` scales model-space vertex heights when calculating wind and interaction weights without changing the rendered geometry. Use values above `1.0` for low-profile models such as flowerbeds. It defaults to `1.0`.
- `interaction_height_scale_multiplier` scales vertex heights only for interaction weighting. It can keep low-profile model parts responsive without changing their wind motion. It defaults to `1.0`.
- `sway_strength_multiplier` scales wind bending for this profile. It defaults to `1.0`.
- `interaction_strength_multiplier` scales entity-driven bending. It defaults to `1.0`.

The three multipliers accept values from `0.0` through `4.0`. Priority accepts values from `-10000` through `10000`.
`height_scale` and `interaction_height_scale_multiplier` accept values from `0.0625` through `16.0`.

## Opting a block out

A high-priority `none` profile prevents a block from inheriting a broader tag-based profile:

```json
{
  "type": "none",
  "priority": 1000,
  "blocks": "examplemod:rigid_flower_statue"
}
```

Where Winds Blow ships separate default profiles for leaves, grass, general foliage, crops, tall foliage, and hanging foliage under `assets/where_winds_blow/where_winds_blow/foliage_profiles/`.
