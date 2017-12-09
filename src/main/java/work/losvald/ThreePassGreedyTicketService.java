package work.losvald;

import java.time.Duration;
import java.util.*;

/**
 * A simple and space-efficient implementation that finds the assigns
 * frontmost seats in three passes:
 *
 * 1) make _all_ seats contiguous if possible, otherwise
 * 2) allocate seats in _groups of two_ and
 * 3) allocate remaining (orphan) seats
 *
 * Algorithm Complexity per hold of N seats
 * -------------------------------------------
 *   Space: numCols/8 + O(1) bytes
 *   Time: O(numRows*numCols)
 *
 */
public abstract class ThreePassGreedyTicketService extends BaseTicketService {
  public ThreePassGreedyTicketService(int numRows, int numCols, Duration holdExpiration) {
    super(new DefaultSeatLayout(numRows, numCols), holdExpiration);
  }

  @Override
  protected Allocator createAllocator(SeatLayout layout) {
    return new Allocator(layout);
  }

  private static class Allocator extends BaseTicketService.Allocator {
    @Override
    public boolean allocate(int numSeats, SeatHold hold) {
      for (int minSize : new int[]{numSeats, 2, 1}) {
        int numCols = layout.getSeatsPerRowCount();
     pass:
        for (int row = 0; row < layout.getRowCount(); row++) {
          for (int col1 = 0, col3; col1 < numCols; col1 = col3) {
            col3 = col1 + 1;
            while (col3 < numCols && used[row].get(col3 - 1) == used[row].get(col3))
              col3++;
            // loop invariant: _all_ bits in range [col1, col3) are set or clear
            int size = col3 - col1;
            if (size >= minSize && !used[row].get(col1)) {
              int maxSize = Math.min(
                  size / minSize * minSize, // round down (e.g., use 4/5 if minSize=2)
                  numSeats);                // don't use too many
              int col2 = col1 + maxSize;
              used[row].set(col1, col2);
              hold.addRange(layout, row, col1, col2 - 1);
              numSeats -= maxSize;
              if (numSeats == 0)
                return true;
              if (numSeats < minSize)
                break pass;  // skip to the next pass
            }
          }
        }
      }
      assert false : "unreachable (BaseTicketService never overbooks)";
      return false;
    }

    @Override
    public void release(SeatHold hold) {
      hold.forEach((seat) -> used[seat.row].clear(seat.col));
    }

    Allocator(SeatLayout layout) {
      super(layout);
      used = new BitSet[layout.getRowCount()];
      for (int i = 0; i < used.length; i++)
        used[i] = new BitSet(layout.getSeatsPerRowCount());
    }
    private final BitSet[] used;
  }
}
