# Galcon (Galactic Conquest) — Game Rules

Based on GALCN220.BAS (Ver. 2.20, January 2026) by Analog Dial, itself based on GALCON24.EXE by Rick Raddatz (1988).

---

## Overview

Galcon is a **multi-player, turn-based space conquest game** (hot-seat). Players command fleets of ships, capture neutral and enemy planets, build up production, and try to dominate the galaxy. The key twist: **you cannot see your opponent's moves** — screen echo is off during input so the other player cannot peek.

---

## The Universe

- The playing field is a **text-mode grid** (16 columns x 16 rows, configurable).
- The number of planets is **user-configurable** (2 to 40).
- Planets are labeled in order: **A, B, C–Z, 1–9, 0, [, ;, ], /** (up to 40 labels).
- **Planet A** is Player 1's home planet.
- **Planet B** is Player 2's home planet.
- All other planets start as **neutral**.
- Planets are placed randomly on the grid. Only **exact overlap** is prevented — there is **no minimum spacing** between planets.
- After placement, players may regenerate the layout if unsatisfied.

## Planet Properties

Each planet has three attributes:

| Property       | Home Planets (A, B) | Neutral Planets |
|----------------|---------------------|-----------------|
| **Ships**      | 10                  | 1–10            |
| **Production** | 10                  | 3–10            |
| **Kill Ratio** | 40                  | 15–50           |

- **Production**: Number of new ships the planet generates per turn for its owner.
- **Kill Ratio**: A percentage that determines how effective ships are in combat. Higher is better.
- These values are assigned randomly at game start within the ranges shown above.

> **Note:** The kill ratio makes certain planets strategically more valuable — a planet with a high kill ratio produces more lethal ships.

## Turn Structure

The game is played over a **user-specified number of turns**.

Each turn:

1. **Player 1 enters moves** (hidden from Player 2).
2. **Player 2 enters moves** (hidden from Player 1).
3. A move consists of: **send N ships from Planet X to Planet Y**.
4. A player may issue **multiple move orders** per turn from the same or different planets.
5. Ships are **deducted immediately** from the source planet when an order is given — subsequent orders from the same planet see the reduced count.
6. **Sending ships to your own planets is allowed** — this is how you send reinforcements.
7. When done entering moves, press Enter on an empty "From" prompt to end your turn.
8. After both players submit moves, the turn resolves.

### Penultimate Turn

On the second-to-last turn (`turn = maxTurns - 1`), players are asked if they want to **add more turns** to the game. If yes, `maxTurns` is increased accordingly.

## Ship Movement

- Ships travel **2 light-years per turn** (speed, configurable).
- Distance between planets is calculated using a **Euclidean formula**
- Each turn, a fleet's remaining distance is reduced by 2 (the speed). When distance reaches 0 or below, the fleet arrives and combat (or reinforcement) resolves.
- If distance falls between 0 and 1 (exclusive), it is forced to 0.9 to prevent display issues.
- Ships in transit **cannot be recalled**.
- The **attacker's kill ratio** (from the origin planet) is recorded at launch time and travels with the fleet. If the source planet's kill ratio changes later (e.g., via an Event), it does NOT affect fleets already in transit.
- Per the instructions screen: it takes approximately **14 turns** to cross the entire universe.

## Production

- Production happens at the **end of each turn**, after all fleet movement, combat, and events have resolved.
- Each owned planet produces new ships equal to its **production value**.
- **Neutral planets do not produce ships.**
- A planet must be owned by a player to produce.
- A newly captured planet produces on the same turn it was captured (since production runs after combat).

## Combat

When an attacking fleet arrives at a planet:

### Reinforcement

If the arriving fleet belongs to the **same player** who owns the target planet, the ships are **added directly** to the planet garrison. No combat occurs.

### Battle Setup

1. **Attacker Kill**: The kill ratio of the planet the ships were launched from (stored at launch time).
2. **Defender Kill**: The current kill ratio of the planet being attacked.
3. **Home Court Bonus**: A random value from **5 to 31** is added to the defender's kill ratio.
4. **Defender Kill Cap**: The boosted defender kill ratio is **capped at 60**.

```
defenderEffectiveKill = min(planetKill + random(5..31), 60)
```

### Combat Resolution (Per Round)

Combat proceeds **one ship at a time** until one side is eliminated:

```
while defenderShips > 0:
    chanceOfDeath = random(20..80)

    if attackerKill < chanceOfDeath:
        attackerShips -= 1

    if attackerShips == 0: break  // attacker eliminated

    if defenderKill < chanceOfDeath:
        defenderShips -= 1
```

Key points:
- **One random roll per round** (`chanceOfDeath`), used for BOTH attacker and defender checks.
- At most **one ship per side can die per round**.
- The attacker is checked **first** — if the attacker reaches 0, the round ends immediately without checking the defender.
- A **higher kill ratio** means fewer deaths (the kill% is more likely to exceed the chanceOfDeath, so the ship survives).
- The `chanceOfDeath` range is **20 to 80** (values outside this range are re-rolled).

### Battle Outcome

- If **defender ships > 0**: "Planet Is Retained" — defender keeps the planet.
- If **attacker ships > 0**: "Planet Is Invaded" — the attacker takes ownership, remaining attacker ships become the garrison, and they **adopt the planet's kill ratio** going forward.

### Fleet Arrival Order

Fleets do **NOT merge** before combat. Each fleet attacks **separately in sequence**. The Navigator interleaves Player 1 and Player 2 fleet resolutions:
- If Player 1 has more orders, P1 fleets are processed first each cycle.
- If Player 2 has more orders, P2 fleets are processed first each cycle.
- If equal, P1 goes first each cycle.

## Events

Events are random occurrences that can happen **after each individual combat resolution** and **once at the end of fleet processing** (Navigator). They also occur during end-game fleet resolution.

### Event Trigger

- **~33% chance** per trigger point (`random(0..2) == 1`).
- A random planet is selected. The event **only fires** if the planet is owned by a player (not neutral).

### Event Types

A random roll `random(1..90)` determines the event:

| Roll    | Event               | Effect |
|---------|---------------------|--------|
| 67–90   | **Change Production** | Production changes by 1–9. ~50% chance increase, ~50% decrease. Floor at 0. |
| 34–66   | **Change Kill Ratio** | Kill ratio changes by 1–19. ~60% chance increase, ~40% decrease. Floor at 0. |
| 2–33    | **Revolt**          | **No effect** — flavor text only ("A revolt has been thwarted"). |

- Production and kill ratio changes are permanent for that planet.
- There is **no upper cap** on production or kill ratio from events (only a floor of 0).

## Fog of War

The original BASIC version has **no fog of war** — both players see all planet stats (ships, production, kill, owner) on the shared stats screen.

**For the multiplayer web version**, fog of war is implemented:
- Players **can see** enemy planets and their labels on the map.
- Players **can see** enemy ship counts or planet stats (production, kill ratio).
- Players **cannot see** enemy fleets in transit (number of ships, origin, destination, kill ratio).
- You only learn the result of an attack when your ships arrive (or when enemy ships arrive at your planet).

## Scoring

At the end of the game:

```
score = INT(averageKill * totalShips / 8) + (planetsOwned * 50)
```

Where:
- `averageKill` = total kill ratio across all owned planets / number of owned planets
- `totalShips` = total ships on all owned planets (ships in transit are **not counted**)
- `planetsOwned` = number of planets owned

The player with the higher score wins.

## Win Conditions

- The game ends after the configured number of turns.
- **The player with the higher score wins.**
- A player may also win early by **eliminating all opponents** (owning all planets with no enemy fleets in transit).

## Game End — Fleet Resolution

When the turn limit is reached:
1. All remaining **in-transit fleets are resolved** over up to 25 additional movement cycles.
2. Each cycle: fleets move 2 light-years, arriving fleets fight, events may trigger, and **production occurs**.
3. Once all fleets have arrived (or 25 cycles pass), the final score is calculated.

---

## Turn Resolution Order

Each turn resolves in this order:

1. **Player 1 enters moves** (ships deducted immediately from source planets).
2. **Player 2 enters moves** (ships deducted immediately from source planets).
3. **Fleet movement & combat** — All fleets advance 2 light-years. Arriving fleets resolve immediately (reinforcement or combat). After each combat, an **Event** may trigger (~33% chance). Fleets are processed in interleaved order between players.
4. **Final event** — One additional event check at the end of fleet processing.
5. **Production** — All owned planets generate new ships equal to their production value.
