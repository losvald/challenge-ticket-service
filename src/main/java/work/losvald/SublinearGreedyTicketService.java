package work.losvald;

import java.time.Duration;
import java.util.*;

/**
 * A ticket service that uses an efficient _greedy_ algorithm that
 * normally finds a nearly optimal seat group in _sublinear_ time with
 * respect to number of requested seats; its worst-case running time is
 * proportional to the number of rows (with 1-2 empty seats in each).
 *
 * The worst-case running time per hold/reservation is O(numSeats),
 * which is indeed optimal (see analysis below) but often times it is
 * constant.  This is to the contrary of a naive implementation that
 * would examine the whole stage every time in time O(maxNumSeats).
 *
 * Algorithm overview
 * ==================
 *
 * The service implements the following divide-and-conquer algorithm
 * for finding the best group of N requested seats, denoted as find():
 *
 * 1) If there are at least N adjacent seats in a row, choose the row
 *    with best such seats (explained later)
 *
 * 2) Otherwise, split the group into two groups of N/2 and N-N/2
 *    seats, respectively, and _recursively_ find best seats in those
 *
 * This splitting strategy avoids uneven splits that could easily lead
 * to so-called _orphan seats_, because a group of 1 seat is split
 * only when there are no adjacent empty seats.
 *
 * Let _seat range_ define each subgroup of adjacent seats in a row.
 * The best range is choosen whenever step (1) successfully executes
 * according to the following distance from the central seat(s):
 *
 *  numSeatsLeftOrRight + numRowsBehind, if a seat is in the back
 *  numSeatsLeftOrRight + numRowsAhead*2, if a seat is at the front

 * such that at least one seat in the range has the _least distance_;
 * ties are broken by the _smallest_ range of empty seats that are in
 * the _frontmost_ row and in the _leftmost_ position, respectively.
 *
 * E.g., seat ranks for a 5x11 stage are as follows for find(13):
 *
 *   -[[STAGE]]-               -[[STAGE]]-                -[[STAGE]]-
 *   -----------               -----------                -----------
 *   98765456789               98765456789                98765456789
 *   76543234567               76543234567                76543234567
 *   54321012345 ---find(7)--> 54AAAAAAA45 ---find(6)-->  54AAAAAAA45
 *   65432123456               65432123456                65BBBBBB456
 *   76543234567               76543234567                76543234567
 *
 *
 * Why is a greedy algorithm a reasonable trade-off?
 * ---------------------------------------------------
 *
 * The problem of optimal seat placement is at least as hard as the
 * bin packing problem, which is a well-known NP-hard problem,
 * provided that one of the goals is to ensure a group of people is
 * split only when absolutely necessary.  As an example, suppose
 * that in the stage with a 4x4 seat layout,
 *
 *  ....
 *  .x..
 *  .xx.
 *  xxxx
 *
 * we want to hold a group of 5 seats. One seemingly good choice is to
 * maximize the group distance from the center (best) while keeping
 * seats adjacent (horizontally and vertically):
 *
 *  .oo.
 *  .xoo
 *  .xxo
 *  xxxx
 *
 * but it leaves an orphan seat in the top-right position, and forces
 * a group of up to 3 people to be seated behind one another.

 * It is easy to see that the ticket service would need to predict the
 * future in order to be able to guarantee minimize group splits and
 * orhpan (isolated) seats.  Therefore, this implementation chooses a
 * different goal: assign seats greedily but as efficient as possible.
 *
 * Algorithm analysis
 * ==================
 *
 * Assume there is a RxC stage with R rows and C seats in each row.
 * Let (d, r:c1-c2) denote the seats numbered c1 through c2 in row r
 * among which best seat has distance d to the center (see above),
 * where each component is numbered from 0 onward.
 *
 * Asymptotic Complexity per hold of N seats
 * -------------------------------------------
 *   Space: O(R*C) - _optimal_ because expiration may create gaps
 *                   (suppose R*C cusotmers holds 1 seat each,
 *                    then seats at every odd number are reserved,
 *                    then the remaining R*C/2 holds expires)

 *  Time: O(N) - worst-case _optimal_ (consider a hold of R*C/2 seats
 *                       in the above scenario where R*C/2 gaps exist)
 *         O(N/C) - best-case optimal (need to store at least N/C ranges)
 *
 *
 * Data structures
 * ---------------
 *
 * We maintain _priority queues_ for distinct-sized ranges of empty seats,
 * mapped by an array `pq` of size R such that `pq[i]` refers to the
 * priority queue of ranges of exactly `i` empty seats.  It initially
 * contains R entries, e.g., for a 5x11 stage above:
 *
 *   11:  (0, 2:0-10), (1, 3:0-10), (2, 1:0-10), (2, 4:0-10), (4, 0:0-10)
 *   10:
 *   ...
 *   1:
 *
 * Finding a seat range for 13 results in a split into ranges of size
 * 7 and 6, since 13 > C = 11.  Find(7) is executed first, and yields:
 *
 *   11:  (1, 3:0-10), (2, 1:0-10), (2, 4:0-10), (4, 0:0-10)
 *   10:
 *   ...
 *   3:
 *   2:   (4, 2:0-1), (4, 2:9-10)
 *   1:
 *
 * The choice is unambiguous at this step, because 2:0-10 is the only
 * seat range with priority 0 (all other priority queues are empty).
 * Now, row 2 has two ranges of 2 empty seats, numbered 0-1 and 9-10,
 * with both at the distance 4 from the center (see ASCII art above).
 * Find(6) is executed afterward, resulting in the following state of `pq`:
 *
 *   11:  (2, 1:0-10), (2, 4:0-10), (4, 0:0-10)
 *   ...
 *   3:   (4, 3:8-10)
 *   2:   (4, 2:0-1), (4, 2:9-10), (4, 3:0-1)
 *
 * In this case and in general, the allocator needs to examine
 * each the top element of priority queue `pq[i]` for i in range
 *   [N, min(2*N, R)]
 * e.g., pq[7], pq[8], pq[9], pq[10], and pq[11],
 * so a naive implementation would take:
 *    T(N) = N/2 + 2*T(N/2)
 * which is O(N lg N) according to Master's theorem.
 *
 * Amortizing the cost
 * -------------------
 *
 * This algorithms avoids this pitfall by the following trick: if a
 * range of N seats could not be allocated in a previos find() call,
 * _remember that_ and next time check only up to some bound U,
 * while ensuring that recursive calls (child states) are computed
 * in the _decreasing order_ of number of seats in each seat group.
 * This works because priority queues are only being deleted from
 * during a series of find() calls that comprise an allocation
 * (release(), which adds back to them cannot be concurrently called
 * because of the mutex is being held throughout findAndHoldSeats()).
 *
 * Thus, it suffices that each divide-and-conquer step, i.e.,
 * find(N), examines only the priority queue in index range:
 *   [N, min(2*N, U)].
 * E.g., for a 5x11 stage with only available empty ranges of size <4,
 *
 *         find(14)   // cannot find range of 14 (only 11 in each row)
 *        /        \                     (split)
 *    find(7)       find(7)         // still cannot find ranges of 7
 *   /      \        /     \
 * find(4) find(3) find(4) find(3)
 *
 * the first find(7) call marks `pq[7..11]` as empty, setting U=6,
 * that the another find(7) no longer needs to check and runs in O(1).
 * Because number of seats in each level differ by at most one, it
 * sorts each level during _breadth-first_ traversal in O(N) time;
 * hence, sorting levels takes O(N + N/2 + N/4 + ...) = O(N) time.
 *
 * It can be seen that the top-level find(N) call runs in O(N),
 * by allocating a 2*N time units; each descendant call either:
 * - consumes the inherited time "credit" if range allocation succeeds; or
 * - consumes U-N time units, decreases global U to N, and
 *   passes remaining distributes at least N credit to the child calls
 *
 * By induction, the complexity of the find(N) is O(N).
 */
public class SublinearGreedyTicketService extends BaseTicketService {
  public SublinearGreedyTicketService(int numRows, int numCols, Duration holdExpiration) {
    super(new CachedSeatLayout(numRows, numCols), holdExpiration);
  }

  @Override
  protected Allocator createAllocator(SeatLayout layout) {
    return new DivideAndConquerAllocator(layout);
  }

  private static class DivideAndConquerAllocator extends Allocator {
    DivideAndConquerAllocator(SeatLayout layout) {
      super(layout);

      // TODO: Java's PriorityQueue doesn't support O(log N) removal;
      // we would ideally use our own, but in the interest of time
      // use Java's TreeSet (min element lookup is O(log N), though).
      pq = new TreeSet[layout.getRowCount()];
      Comparator<Range> comp = (Range lhs, Range rhs) -> {
            if (lhs.rank < rhs.rank) return -1;
            if (lhs.rank > rhs.rank) return +1;
            if (lhs.row < rhs.row) return -1;
            if (lhs.row > rhs.row) return +1;
            if (lhs.colFrom < rhs.colTo) return -1;
            if (lhs.colFrom > rhs.colTo) return +1;
            return 0;
      };
      for (int i = 0; i < pq.length; i++)
        pq[i] = new TreeSet<Range>(comp);

      this.centerRow = layout.getRowCount() / 2;
      this.centerCol = layout.getSeatsPerRowCount() / 2;
    }

    @Override
    public boolean allocate(int numSeats, SeatHold hold) {
      new Search(numSeats, hold);
      return true;
    }

    @Override
    public void release(SeatHold hold) {
      // TODO
    }

    /**
     * Returns the preference of a seat (i.e., "distance" to the center).
     */
    int d(int row, int col) {
      int horPenalty = Math.abs(col - centerCol);
      int verPenalty = row - centerRow;
      if (verPenalty < 0)
        verPenalty = -2 * verPenalty;
      return horPenalty + verPenalty;
    }

    private final int centerRow, centerCol;

    /**
     * Finds the seat range of size between numSeats and maxSize that
     * is available by examining corresponding priority queues, and
     * updates the hold and a priority queue if it succeeds.
     */
    private boolean allocateRange(int numSeats, int maxSize, SeatHold hold) {
      // TODO check
      return true;
    }

    private final TreeSet<Range> pq[];  // priority queue with binary search

    private static class Range {
      int rank;
      int row, colFrom, colTo;
    }

    private class Search {
      private final SeatHold hold;
      private int maxSize;
      public Search(int numSeats, SeatHold hold) {
        this.hold = hold;
        this.maxSize = numSeats;

        // Breath First Search (see call tree in ASCII art above)
        qSiblings().offer(numSeats);
        do {
          while (!qSiblings().isEmpty()) {
            find(qSiblings().poll());
          }
          swapAndSortChildren();
        } while (!qChildren().isEmpty());
      }

      private void find(int numSeats) {
        if (allocateRange(numSeats, maxSize, hold))
          return;

        maxSize = Math.min(maxSize, numSeats);
        int halfNumSeats2 = numSeats / 2;
        int halfNumSeats1 = numSeats - halfNumSeats2;
        qChildren().offer(halfNumSeats1);
        qChildren().offer(halfNumSeats2);
      }

      // the queue of sibling states -- numSeats is _sorted_
      Queue<Integer> qSiblings() { return queues[curr]; }

      // the queue of child states -- numSeats differ by _at most 1_
      Queue<Integer> qChildren() { return queues[1 - curr]; }

      // swaps in the queue of child states and sorts it in _linear_ time
      void swapAndSortChildren() {
        curr = 1 - curr;
        sortIntsOfDiff1Descending(queues[curr]);
      }

      private LinkedList<Integer>[] queues;
      private int curr;
    }
  }

  // internal methods/classes exposed within a package for unit testing

  // Sorts the list with at most 2 distinct values in linear time.
  static void sortIntsOfDiff1Descending(LinkedList<Integer> lst) {
    Iterator<Integer> iter = lst.iterator();
    if (!iter.hasNext())
      return;

    int max = iter.next(), min = max;
    while (iter.hasNext()) {
      int cur = iter.next();
      if (cur > max) max = cur;
      if (cur < min) min = cur;
    }

    int minCount = 0;
    for (iter = lst.iterator(); iter.hasNext(); ) {
      if (iter.next() == min) {
        iter.remove();
        minCount++;
      }
    }

    while (minCount-- > 0)
      lst.add(min);
  }

  /**
   * A seat layout (i.e., stage) with pre-allocating Seat objects.
   */
  static class CachedSeatLayout implements SeatLayout {
    @Override
    public Seat at(int row, int num) {
      if (row < 0 || row >= getRowCount() ||
          num < 0 || num >= getSeatsPerRowCount()) {
        throw new IllegalArgumentException(
            row + " " + num + " outside the " +
            getRowCount() + "x" + getSeatsPerRowCount() + " stage");
      }
      return this.seats[row][num];
    }

    @Override
    public int getRowCount() {
      return seats.length;
    }

    @Override
    public int getSeatsPerRowCount() {
      return seats[0].length;
    }

    CachedSeatLayout(int rows, int cols) {
      if (cols <= 0 || rows <= 0)
        throw new IllegalArgumentException("Must have at least one row and column");
      this.seats = new Seat[rows][cols];
      for (int r = 0; r < rows; ++r)
        for (int c = 0; c < cols; ++c)
          this.seats[r][c] = new Seat(r, c);
    }

    private Seat[][] seats;
  }
}
