# Create: Phantom Stress Fix

Fixes [Create issue #10516](https://github.com/Creators-of-Create/Create/issues/10516): after a world or chunk loads, a kinetic network can show stress that nothing applies, and it grows with every reload until the network is overstressed. Not affiliated with the Create team.

A network that loads from disk starts from the stress it saved, and each block takes its own share back out when it loads. If something changes the network while it is loading (a redstone link switching a gearshift right after the chunk loads, for example), some blocks join the network before their own load step, Create skips that step for them, and their share is never taken out. This addon takes it out.

Config: `config/phantom_stress_fix.properties` (`enabled`).

## Tests

GameTests unload and reload a motor, gearshift and fan the way a chunk load does, switching the gearshift right after the load. With the fix the network's stress stays at 32 after three reloads; the control run without the fix has to show phantom stress (it climbs to 96). A third test loads the transmitter first, so nothing switches.

MIT.
