package work.losvald;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static work.losvald.TicketService.*;
import static work.losvald.SublinearGreedyTicketService.CachedSeatLayout;

import org.junit.Before;
import org.junit.Test;

public class CachedSeatLayoutTest {
  @Test
  public void test3x2InBounds() {
    SeatLayout layout = new CachedSeatLayout(3, 2);
    assertEquals(3, layout.getRowCount());
    assertEquals(2, layout.getSeatsPerRowCount());
    assertEquals(new Seat(2, 1), layout.at(2, 1));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalRow() {
    new CachedSeatLayout(2, 1).at(0, 1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIllegalColumn() {
    new CachedSeatLayout(3, 4).at(3, 3);
  }
}
