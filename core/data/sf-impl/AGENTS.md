# SF Data Pipeline (sf-impl)

## Data Sources

Two APIs. Source `.json` files live in `sf-data/` (module root, tracked in git). The generated
`park_buddy_db` lives in `src/commonMain/resources/sf-data/` (bundled into the app via KMP
resources). At runtime, `refreshData()` downloads fresh JSON from the network and rebuilds the DB.

| Source | API | Records | Key |
|--------|-----|---------|-----|
| Street centerlines | Socrata `3psu-pn9h` | 16,376 | CNN (authoritative geometry) |
| Street cleaning | ArcGIS Layer 3 | 37,904 | CNN + side |
| Metered blockfaces | ArcGIS Layer 12 | 3,141 | BLOCKFACE_ID |
| Meters | ArcGIS Layer 11 | 38,519 | POST_ID, bridges BLOCKFACE_ID↔CNN |
| Time-limited regs | ArcGIS Layer 9 | 6,891 | Spatial match only (no CNN) |
| Other regs | ArcGIS Layer 10 | 871 | Spatial match only (no CNN) |
| Meter policies | Socrata `qq7v-hds4` | ~123K | POST_ID |
| Meter schedules | Socrata `6cqg-dxku` | ~72K | POST_ID |
| Blockface rates | ArcGIS ODS Layer 4 | 2,795 | BLOCKFACE_ID |
| **Exclusions** | Local `exclusions.json` | 410 | CNN (direct lookup) |

ArcGIS base: `https://services.sfmta.com/arcgis/rest/services/Parking/`
Socrata base: `https://data.sfgov.org/`

## Pipeline (buildDbFromData in ParkingRepositoryImpl)

1. **Centerline backbone**: All 16,376 active CNNs. Excludes classcode 1 (freeways) and 6 (ramps).
   This is the ONLY geometry source for `CoordinateMatcher`.
2. **Sweeping index**: Build `cleaningByCnnSide` map. No geometry contribution.
3. **Blockface index**: Index blockface metadata by BLOCKFACE_ID.
4. **Meter bridges**: Build `blockfaceToCnn` from meter `STREET_SEG_CTRLN_ID` field.
5. **Seed sweeping contexts**: Create `UnifiedSegmentContext` for each CNN+side with sweeping data.
6. **Index meter schedules/policies**: Group by POST_ID.
7. **Merge meter data**: For each metered blockface, find CNN (explicit or spatial), merge schedules.
8. **Spatial-match regulations**: Match regulation polylines to backbone CNNs via `matchPolyline`.
   Forbidden regs get per-CNN ratio filter: skip if regulation < 40% of block length.
9. **Apply exclusions**: For each CNN in `exclusions.json`, inject `Forbidden(NO_PARKING)` 24/7.
   Direct CNN lookup, no spatial matching. Covers transit-only lanes (Market, Mission, Geary, etc.)
   and other known unparkable streets (Embarcadero) that SFMTA APIs don't have regulation data for.
10. **Merge sweeping-only**: Absorb adjacent sweeping-only CNNs into regulated neighbors.
11. **Resolve timelines**: `TimelineResolver` flattens all intervals per CNN+side into entities.

Nameless contexts fall back to `matcher.getStreetNameByCnn()` (from centerline data).

## Spatial Matching

`CoordinateMatcher` is a grid-based spatial index (0.001° cells, ~90m). Used for:
* **Regulations → CNN**: The only place spatial matching is needed. Regs have no CNN field.
* **Blockface → CNN** (fallback): When explicit CNN bridge doesn't exist.

Sweeping and meters have explicit CNN fields and don't need spatial matching.
Exclusions use direct CNN lookup (no spatial matching).

### Gotchas
* **Cross-street pollution**: At intersections, a regulation on one street can match an adjacent
  street's CNN. The 40% ratio filter catches this for no-parking regs.
* **Freeway overpasses**: Street-level regulations can match elevated highway CNNs. Fix: exclude
  classcode 1/6 from backbone.
* **Divided roads**: CNN suffixes 101/201 are left/right sides. `matchPolyline` returns closest CNN
  per sample point, so only one side may match per point.
* **Threshold**: `MATCHING_THRESHOLD_METERS = 20.0`

## Exclusions (`sf-data/exclusions.json`)

CNN-based no-parking overrides for streets that SFMTA APIs don't cover with regulation data.
Generated from SFMTA transit lane data (DataSF/master/FeatureServer/10) plus manually identified
gap blocks (Market St east of 3rd, Embarcadero). Currently 410 CNNs across ~30 streets.

To add a new exclusion: add `{"cnn": "<CNN>", "street": "<STREET NAME>"}` to the JSON array,
regenerate the DB, bump `DB_VERSION`.

## Known Limitations

* **Sub-CNN segments**: Data keyed by (CNN, side) = entire block face. A partial no-parking zone
  (bus stop on 30% of block) can't be represented separately.

## Local DB Verification (fast loop, no device needed)

Regenerate the pre-built DB from source `.json` files, then query it directly:

```bash
DB=core/data/sf-impl/src/commonMain/resources/sf-data/park_buddy_db

# Regenerate after updating .json source files or the pipeline:
./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*GeneratePrebuiltDb*"

# Total spot count
sqlite3 $DB "SELECT count(*) FROM parking_spots;"

# Check a specific street
sqlite3 $DB "SELECT objectId, streetName, blockLimits, timeline FROM parking_spots WHERE LOWER(streetName) = 'market st' ORDER BY objectId;"

# Spots with empty timelines (sweeping-only, show as "Free Parking")
sqlite3 $DB "SELECT streetName, count(*) FROM parking_spots WHERE timeline = '[]' GROUP BY streetName ORDER BY count(*) DESC LIMIT 20;"

# Orphan count (regulations that didn't match any CNN)
sqlite3 $DB "SELECT count(*) FROM parking_spots WHERE objectId LIKE 'cnn_reg_%';"
```

## Updating the Pre-built Database

**New source data** (API changes, new streets, updated regulations):
1. Update `.json` files in `sf-data/`.
2. Regenerate: `./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*GeneratePrebuiltDb*"`
3. Bump `DB_VERSION` in `ParkBuddyDatabase.kt`.
4. Commit updated `.json` files, `park_buddy_db`, and version bump.

**Pipeline bug fix** (parsing, spatial matching, timeline resolution, etc.):
1. Fix the bug.
2. Regenerate from existing `.json` files (same command).
3. Bump `DB_VERSION` in `ParkBuddyDatabase.kt`.
4. Commit code fix, `park_buddy_db`, and version bump.

Bumping `DB_VERSION` is what triggers existing installs to replace their on-device DB on next launch.

## Test Commands

```bash
./gradlew :core:data:sf-impl:testAndroidHostTest                                       # All tests
./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*ParkingRepositoryImplTest*"  # Pipeline
./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*GeneratePrebuiltDb*"         # Regenerate pre-built DB
```
