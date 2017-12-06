package work.losvald;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;


public interface TicketService {
  /**
   * A group of seats held, uniquely identified by getId().
   *
   * The identifier must be cryptographically secure, so that seats
   * held by customer A cannot be overtaken by another customer who
   * happen to guess the identifier and customer A's email.
   *
   * For convenience, this class implements deep equality based on
   * both the identifier and the set of held seats.
   */
  public static final class SeatHold {
    /**
     * Identifies the held seat group.
     *
     * @return an integer that serves as a unique identifier
     */
    public int getId() { return id; }

    public String toString() {
      String commaSeparatedSeats = seats.stream().map(Object::toString)
          .collect(Collectors.joining(","));
      return String.format("#%08X@%s", id, commaSeparatedSeats);
    }

    public boolean equals(final Object that0) {
      if (!(that0 instanceof SeatHold))
        return false;
      SeatHold that = (SeatHold)that0;
      return this.id == that.id && // compare IDs first (fast path)
          this.seats.equals(that.seats);
    }

    /**
     * Returns the number of seats in this hold.
     *
     * @return a non-negative number, provided that the hold is finalized
     */
    public int seatCount() { return seats.size(); }

    /**
     * Calls a consumer (basically, a one-argument function) for each seat.
     *
     * @param fn an anonymous function, a method reference or a Consumer
     */
    public void forEach(java.util.function.Consumer<? super Seat> fn) {
      seats.forEach(fn);
    }

    // Internal logic hidden from the user of the TicketService

    void addRange(SeatLayout layout, int row, int numMin, int numMax) {
      for (int num = numMin; num <= numMax; num++)
        seats.add(layout.at(row, num));
    }

    /**
     * Returns a perfect hash of a seat group help in format like:
     *   2:5-6,9|3:6-9  // seats 5 and 6 in row 2, 9th seat in row 2
     *                  // and seats 6 through 9 in row 3
     * Used mostly by unit tests.
     */
    String hashSeats() {
      int lastRow = -1, lastCol = -1;
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      boolean pendingRange = false;
      for (Seat seat : seats) {
        if (seat.row == lastRow) {
          boolean oldPendingRange = pendingRange;
          pendingRange = (seat.col == lastCol + 1);
          if (!pendingRange) {
            maybeAppendDashAndSeatNum(oldPendingRange, sb, lastCol);
            sb.append(',').append(seat.col);
          }
        } else {
          maybeAppendDashAndSeatNum(pendingRange, sb, lastCol);
          pendingRange = false;
          if (!first) sb.append('|');
          sb.append(seat.row).append(':').append(seat.col);
        }
        lastCol = seat.col;
        lastRow = seat.row;
        first = false;
      }
      maybeAppendDashAndSeatNum(pendingRange, sb, lastCol);
      return sb.toString();
    }
    private static void maybeAppendDashAndSeatNum(
        boolean pendingRange, StringBuilder sb, int lastNum) {
      if (pendingRange) sb.append('-').append(lastNum);
    }

    SeatHold(int id, Seat... seats) {  // SeatLayout, int...
      this.id = id;
      this.seats = Arrays.stream(seats)  //.mapToObj(x -> layout.fromIndex(x))
          .collect(Collectors.toCollection(TreeSet::new));
    }

    // fields used internally by the service
    java.time.Instant expirationTime;
    String customerEmail;

    private final int id;
    private final SortedSet<Seat> seats;
  }

  /**
   * The value-based class used internally to represents a seat.
   *
   * It is comparable in a domain-specific way: two seats are adjacent
   * are iff they are in the same row and its number within the row
   * (i.e., column) differs by one.  Its string representation is:
   *
   *   (ROW,NUMBER)  // both indexes start from 0
   */
  static final class Seat implements Comparable<Seat> {
    @Override
    public String toString() {
      return "(" + Integer.toString(row) + "," + Integer.toString(col) + ")";
    }

    @Override
    public final int compareTo(final Seat that) {
      if (this.row < that.row) return -1;
      if (this.row > that.row) return 1;
      if (this.col < that.col) return -1;
      if (this.col > that.col) return 1;
      return 0;
    }

    @Override
    public final boolean equals(final Object that0) {
      if (!(that0 instanceof Seat))
        return false;
      Seat that = (Seat)that0;
      return this == null ? that == null :
          (this.row == that.row && this.col == that.col);
    }

    // Implementation details (package private):
    // - storing both row and column instead of a 0-based index
    //   allows Seat class to be _decoupled_ from the SeatLayout
    //   (otherwise, we would need to check if |idx1-idx2|==1 and
    //    max(idx1,idx2) % SeatLayout#getSeatsPerRowCount() == 0)

    Seat(int row, int col) {
      this.row = row;
      this.col = col;
    }

    final int row, col;
  }

  /**
   * Factory that maps coordinates (row, column) into Seat objects.
   */
  static interface SeatLayout {
    /**
     * Returns the total number of rows on the stage.
     *
     * @return a positive integer
     */
    int getRowCount();

    /**
     * Returns the number of seats in each row.
     *
     * @return a positive integer
     */
    int getSeatsPerRowCount();

    /**
     * Creates or retrieves the seat at a specific row and column.
     *
     * @param row a non-negative integer in range [0, getRowCount())
     * @param num a non-negative integer in range [0, getSeatsPerRowCount())
     * @return a newly created or cached seat
     * @throws IllegalArgumentException
     */
    Seat at(int row, int num);

    /**
     * Converts the 0-based index into a seat.
     *
     * @param idx a non-negative integer
     * @return a seat that conforms to the layout (i.e., is valid)
     */
    default Seat fromIndex(int idx) {
      final int n = getSeatsPerRowCount();
      return at(idx / n, idx % n);
    }

    /**
     * Converts the seat into a 0-based index without checking bounds.
     *
     * @param seat a non-null seat
     * @return a non-negative integer
     */
    default int getIndex(Seat seat) {
      java.util.Objects.requireNonNull(seat);
      return getSeatsPerRowCount() * seat.row + seat.col;
    }
  }

  /**
   * The number of seats in the venue that are neither held nor reserved
   *
   * @return the number of tickets available in the venue
   */
  int numSeatsAvailable();

  /**
   * Find and hold the best available seats for a customer
   *
   * @param numSeats the number of seats to find and hold
   * @param customerEmail unique identifier for the customer
   * @return a SeatHold object identifying the specific seats and related
   information
  */
  SeatHold findAndHoldSeats(int numSeats, String customerEmail);

  /**
   * Commit seats held for a specific customer
   *
   * @param seatHoldId the seat hold identifier
   * @param customerEmail the email address of the customer to which
   * the seat hold is assigned
   * @return a reservation confirmation code
   */
  String reserveSeats(int seatHoldId, String customerEmail);
}
