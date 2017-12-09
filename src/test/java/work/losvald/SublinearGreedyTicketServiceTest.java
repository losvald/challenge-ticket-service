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



  @Test
  public void testSortIntsOfDiff1DescendingMaxFirst() {
    LinkedList<Integer> lst =
        new LinkedList<>(Arrays.asList(7, 6, 7, 6, 6, 7, 7));
    sortIntsOfDiff1Descending(lst);
    assertEquals(Arrays.asList(7, 7, 7, 7, 6, 6, 6), lst);
  }
}
