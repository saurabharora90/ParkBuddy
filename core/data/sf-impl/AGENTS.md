# SF Data Pipeline (sf-impl)

## Data Sources

Two APIs, all responses bundled as gzipped JSON in `src/commonMain/resources/sf-data/`:

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
9. **Merge sweeping-only**: Absorb adjacent sweeping-only CNNs into regulated neighbors.
10. **Resolve timelines**: `TimelineResolver` flattens all intervals per CNN+side into entities.

Nameless contexts fall back to `matcher.getStreetNameByCnn()` (from centerline data).

## Spatial Matching

`CoordinateMatcher` is a grid-based spatial index (0.001° cells, ~90m). Used for:
* **Regulations → CNN**: The only place spatial matching is needed. Regs have no CNN field.
* **Blockface → CNN** (fallback): When explicit CNN bridge doesn't exist.

Sweeping and meters have explicit CNN fields and don't need spatial matching.

### Gotchas
* **Cross-street pollution**: At intersections, a regulation on one street can match an adjacent
  street's CNN. The 40% ratio filter catches this for no-parking regs.
* **Freeway overpasses**: Street-level regulations can match elevated highway CNNs. Fix: exclude
  classcode 1/6 from backbone.
* **Divided roads**: CNN suffixes 101/201 are left/right sides. `matchPolyline` returns closest CNN
  per sample point, so only one side may match per point.
* **Threshold**: `MATCHING_THRESHOLD_METERS = 20.0`

## Known Limitations

* **The sweeping-only dilemma**: Many CNNs have street sweeping data but zero regulations or
  meters. Most of these are genuine residential streets where parking is free (the city just sweeps
  them). We show these as "Free Parking" which is correct for residential areas. However, the same
  pattern also appears on major arterials like Market St and Van Ness Ave, which are NOT parkable
  but have no regulation data to prove it. We cannot hide sweeping-only spots globally because that
  would remove thousands of valid residential streets. There is no SFMTA API that distinguishes
  "parkable sweeping-only" from "non-parkable sweeping-only." Transit lane data was attempted but
  is incomplete and causes spatial matching collateral damage. This remains unsolved.
* **Sub-CNN segments**: Data keyed by (CNN, side) = entire block face. A partial no-parking zone
  (bus stop on 30% of block) can't be represented separately.

## Useful Queries

```bash
# Orphan count (regulations that didn't match any CNN)
sqlite3 /tmp/parkbuddy.db "SELECT count(*) FROM parking_spots WHERE objectId LIKE 'cnn_reg_%';"

# Check a specific street
sqlite3 /tmp/parkbuddy.db "SELECT objectId, streetName, blockLimits, timeline FROM parking_spots WHERE LOWER(streetName) = 'harrison st' ORDER BY objectId;"

# Spots with empty timelines (sweeping-only, show as "Free Parking")
sqlite3 /tmp/parkbuddy.db "SELECT streetName, count(*) FROM parking_spots WHERE timeline = '[]' GROUP BY streetName ORDER BY count(*) DESC LIMIT 20;"
```

## Test Commands

```bash
./gradlew :core:data:sf-impl:testAndroidHostTest                                       # All tests
./gradlew :core:data:sf-impl:testAndroidHostTest --tests "*ParkingRepositoryImplTest*"  # Pipeline
```
