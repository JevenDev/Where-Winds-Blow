<div align="center">

<h2><strong>Where Winds Blow, I'd go with you.</strong></h2>

</div>

<div align="center">

<a href="https://github.com/JevenDev/Where-Winds-Blow" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/cozy/supported/neoforge_64h.png" alt="Available for NeoForge"></a>
<br>
<a href="https://github.com/JevenDev/Where-Winds-Blow" target="_blank" rel="noopener noreferrer"><img src="https://raw.githubusercontent.com/intergrav/devins-badges/refs/heads/v3/assets/compact-minimal/available/github_46h.png" alt="Available on GitHub"></a>

### The mod is <i><u>NOT</u></i> required on servers if you only want the visual effects. You can use the mod client-side for visual effects only.

</div>

Where Winds Blow is a Vanilla+ NeoForge mod for Minecraft focused on making biomes feel denser, softer, and more atmospheric without replacing vanilla terrain generation.

Instead of turning the overworld into a completely different biome set, the mod builds on top of vanilla with data-driven grass placement, new foliage variants, dry-grass transitions, and optional responsive foliage rendering on the client.

- Adds new grass variants that fit vanilla palettes
- Backported desert dry grass from 1.21.5
- Thickens grassy biomes with configurable biome modifier worldgen
- Supports dry, dead, sparse, medium, dense, and overgrown grass families
- Exposes biome placement through datapack JSON for easy pack customization
- Adds a client config screen for tuning foliage density behavior and client-side effects
- Includes responsive foliage rendering and shader hooks, with Sodium and Iris compatibility support

![features](https://cdn.modrinth.com/data/cached_images/ec0e4dc78ec1a652eb11b233dd2926f7461fe770.png)

## What It Adds

Where Winds Blow adds several lightweight plant blocks designed to blend into vanilla biomes:

- Overgrown Grass
- Flat Grass
- Flat Dead Grass
- Short Dead Grass
- Short Dry Grass
- Tall Dry Grass

These blocks are used both as placeable natural blocks and as worldgen building blocks for the mod's biome features.

## Worldgen

Grass generation is split into separate families so packs can tune biome coverage with more control:

- Dense short grass
- Medium short grass
- Sparse short grass
- Tall grass
- Overgrown grass
- Overgrown grass fields
- Short dry grass
- Tall dry grass
- Dead grass
- Wild wheat (disabled by default)

## Responsive Foliage

On the client, Where Winds Blow can wrap supported foliage models with responsive movement behavior so grassy areas and leaf canopies feel less static.
This is meant to stay lightweight and still look like Minecraft, not turn the game into a completely different rendering style.
Wind sway can be toggled separately for grass/plants and leaf blocks.

## Configuration

Main config file:

- Singleplayer/client: `config/where_winds_blow-common.toml`
- Dedicated server: `<server root>/config/where_winds_blow-common.toml`

You can tune or disable the major worldgen groups independently, including:

- Dense grass
- Medium grass
- Sparse grass
- Tall grass
- Short dry grass
- Tall dry grass
- Dead grass
- Overgrown grass
- Wild wheat

Worldgen config changes require a world reload or restart and only affect newly generated chunks.

## Datapacks and Pack Support

Where Winds Blow is built to be pack-friendly.

Biome membership, biome modifiers, and placed/configured features all live in resource JSON so modpacks and datapacks can move foliage families into different biomes, change patch density, or swap out what each patch places.

Pack-facing documentation lives here:

- [Datapack notes](DATAPACKS.md)

Key resource areas:

- `data/where_winds_blow/tags/worldgen/biome/`
- `data/where_winds_blow/neoforge/biome_modifier/`
- `data/where_winds_blow/worldgen/configured_feature/`
- `data/where_winds_blow/worldgen/placed_feature/`
![compatibility](https://cdn.modrinth.com/data/cached_images/1252c11050b7daf8b8621712b58dd1005e7ba982.png)

## Compatibility

- Built for NeoForge `1.21.1`
- Uses vanilla-style blocks, tags, and biome worldgen hooks
- Includes optional compatibility paths for Sodium and Iris-related rendering behavior

Compatibility may vary with mods that heavily replace terrain generation, foliage rendering, or biome feature injection.

![credits & license](https://cdn.modrinth.com/data/cached_images/5fd3ad80e342e6985dd6ebda1f7afd9c48749fce.png)

## Modpacks

You may use this mod in modpacks, videos, servers, and other projects. A link back to the Modrinth page is appreciated.

## Credits

Created by me :D

## License

All Rights Reserved.

Feel free to use this mod in modpacks, videos, etc. Just provide a link back to this page if possible :)

Please don't port this mod without express permission from me.

For any general queries/unlisted questions, DM me on Twitter (@prodbyjvn) / Discord (ijvn).

<div align="center">

  <p><strong><em>Warning: this mod ONLY exists on Modrinth & CurseForge as of June 2026. Any sites hosting this mod outside of Modrinth/CurseForge are not official releases.</em></strong></p>

</div>
