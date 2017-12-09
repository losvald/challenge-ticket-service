package work.losvald;

import static org.junit.Assert.*;
import static work.losvald.TicketService.*;
import static work.losvald.SublinearGreedyTicketService.*;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.*;

public class SublinearGreedyTicketServiceTest {

  // Unlike in the case of ThreePassGreedyTicketService,
  // this test interacts with the Allocator _directly_,
  // thus avoiding mocking the BaseTicketService's clock.
  //
  // (After all, the TicketService's logic modulo allocator
  // is already rigorously covered by 2 other unit tests.)

  static DivideAndConquerAllocator createAlloc(int numRows, int numCols) {
    return new SublinearGreedyTicketService(numRows, numCols, Duration.ofHours(1))
        .alloc;
  }

  @Test
  public void testSeatPreference() {
    int numRows = 5, numCols = 11;
    DivideAndConquerAllocator alloc = createAlloc(numRows, numCols);
    StringBuilder sb = new StringBuilder();
    for (int row = 0; row < numRows; row++, sb.append('\n'))
      for (int col = 0; col < numCols; col++) {
        sb.append(alloc.d(row, col));
      }
    assertEquals(
        "98765456789\n" +
        "76543234567\n" +
        "54321012345\n" +
        "65432123456\n" +
        "76543234567\n",
        sb.toString());

    int colMid = numCols / 2;
    assertEquals(5, alloc.d(0, 2, 5, colMid));  // left overlap
    assertEquals(4, alloc.d(1, 7, 10, colMid)); // right overlap
    assertEquals(0, alloc.d(2, 5, 6, colMid));  // middle overlap
    assertEquals(1, alloc.d(3, 0, numCols, colMid));  // row-spanning
  }

  @Test
  public void testInitialState() {
    DivideAndConquerAllocator alloc = createAlloc(5, 11);
    assertEquals(
        "11: (0, 2:0-10) (1, 3:0-10) (2, 1:0-10) (2, 4:0-10) (4, 0:0-10)",
        alloc.toString());
  }

  @Test
  public void testSortIntsOfDiff1DescendingMaxFirst() {
    LinkedList<Integer> lst =
        new LinkedList<>(Arrays.asList(7, 6, 7, 6, 6, 7, 7));
    sortIntsOfDiff1Descending(lst);
    assertEquals(Arrays.asList(7, 7, 7, 7, 6, 6, 6), lst);
  }
}
