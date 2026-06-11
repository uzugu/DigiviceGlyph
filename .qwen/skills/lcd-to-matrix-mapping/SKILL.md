---
name: lcd-to-matrix-mapping
description: Map LCD content to a 25x25 matrix without cropping, preserving all pixels
source: auto-skill
extracted_at: '2026-06-10T22:07:17.157Z'
---

# LCD-to-Matrix Mapping

When mapping LCD content (e.g., 32x16) to a 25x25 matrix, the common pitfall is losing pixels through aggressive column grouping or centering that leaves rows empty.

## The problem

- **FIT_HEIGHT too small**: If FIT_HEIGHT < MATRIX_SIZE, LCD rows compress into fewer matrix rows, leaving gaps.
- **FIT_Y_OFFSET centering**: A non-zero offset pushes content away from the top, leaving rows above empty.
- **Column grouping losing pixels**: Mapping matrix columns to LCD ranges skips LCD columns that land between ranges.

## The fix

```kotlin
private const val FIT_HEIGHT = MATRIX_SIZE  // Fill full height
private const val FIT_Y_OFFSET = 0          // Start at top
private const val FIT_X_OFFSET = 0          // Start at left

// Row: map each LCD row to its own matrix row (stretch)
val dstRow = (row * MATRIX_SIZE / LCD_HEIGHT).coerceIn(0, MATRIX_SIZE - 1)

// Column: map each LCD column to its own matrix column (nearest-neighbor)
val matrixCol = (lcdCol * FIT_WIDTH / LCD_WIDTH).coerceIn(0, FIT_WIDTH - 1)
```

## Algorithm

1. **Clear** the matrix to the background color (Color.BLACK).
2. **Extract** lit pixels from the LCD bitmap into a `lit[y][x]` boolean grid.
3. **For each LCD row**:
   - Compute `dstRow = (row * MATRIX_SIZE / LCD_HEIGHT)` — stretches LCD across full height.
   - **For each LCD column**:
     - Compute `matrixCol = (lcdCol * FIT_WIDTH / LCD_WIDTH)` — maps each LCD column to its own matrix column.
     - **OR together** all LCD columns that share the same `matrixCol` (for columns immediately following the current one).
     - **Set** the matrix pixel at `(FIT_X_OFFSET + matrixCol, FIT_Y_OFFSET + dstRow)` to WHITE if lit, BLACK otherwise.
4. **Return** the matrix bitmap.

## Key invariants

- Every LCD column maps to exactly one matrix column → no cropped columns.
- Every LCD row maps to exactly one matrix row → no cropped rows.
- Multiple LCD columns/rows mapping to the same matrix position are OR'd together → no lost pixels.
- FIT_X_OFFSET = 0 and FIT_Y_OFFSET = 0 → content starts at top-left of the matrix.

## Verification

- For LCD width 32 → FIT_WIDTH 25: matrix columns 0–24 are all written to.
- For LCD height 16 → MATRIX_SIZE 25: LCD rows 0–15 map to matrix rows 0–24 (with some rows getting 2 LCD rows, others 1).
- Check that `lcdCol * FIT_WIDTH / LCD_WIDTH` is monotonic (it is, since FIT_WIDTH > 0).
- Check that `(LCD_WIDTH - 1) * FIT_WIDTH / LCD_WIDTH` is within `[0, FIT_WIDTH - 1]`.
