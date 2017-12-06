package work.losvald;

import static org.junit.Assert.*;
import static work.losvald.TicketService.*;

import org.junit.Before;
import org.junit.Test;


public class TicketServiceTest {

  static final class SimpleSeatLayout implements SeatLayout {
    @Override public Seat at(int row, int num) { return new Seat(row, num); }
    @Override public int getRowCount() { return rowCount; }
    @Override public int getSeatsPerRowCount() { return colCount; }

    SimpleSeatLayout(int rowCount, int colCount) {
      this.rowCount = rowCount;
      this.colCount = colCount;
    }
    final int rowCount, colCount;
  }

  final SeatLayout stage3x4 = new SimpleSeatLayout(3, 4);

  @Test
  public void testSeatToString() {
    assertEquals("(3,4)", new Seat(3, 4).toString());
  }

  @Test
  public void testSeatHoldComparable() {
    assertNotEquals(new SeatHold(4), new SeatHold(3));
    assertEquals(new SeatHold(1), new SeatHold(1));

    SeatHold lhs = new SeatHold(2, new Seat(2, 1), new Seat(0, 3));
    SeatHold rhs = new SeatHold(2, new Seat(0, 3), new Seat(2, 1));
    assertEquals(lhs, rhs);

    // add two different seats and verify they holds did not
    lhs.addRange(stage3x4, 1, 2, 3);
    assertNotEquals(lhs, rhs);
    rhs.addRange(stage3x4, 0, 1, 2);
    assertNotEquals(lhs, rhs);

    // add the missing seat to each hold and verify they are equal again
    lhs.addRange(stage3x4, 0, 1, 2);
    rhs.addRange(stage3x4, 1, 2, 3);
    assertEquals(lhs, rhs);
  }

  @Test
  public void testSeatHoldToString() {
    assertEquals("#00000002@(0,3),(2,1)", new SeatHold(
        2, new Seat(2, 1), new Seat(0, 3)).toString());
  }

  @Test
  public void testSeatHoldHashSeats() {
    assertEquals("2:5-6,9|3:6-9", new SeatHold(
        0,
        new Seat(2, 9),
        new Seat(2, 6),
        new Seat(3, 8),
        new Seat(3, 6),
        new Seat(3, 9),
        new Seat(3, 7),
        new Seat(2, 5)).hashSeats());

    assertEquals("1:1,3-4|2:5|3:6", new SeatHold(
        0,
        new Seat(1, 1),
        new Seat(1, 3),
        new Seat(1, 4),
        new Seat(2, 5),
        new Seat(3, 6)).hashSeats());
  }
}
