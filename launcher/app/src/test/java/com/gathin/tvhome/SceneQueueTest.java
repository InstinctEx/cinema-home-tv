package com.gathin.tvhome;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class SceneQueueTest {
  @Test
  public void shuffledCycleHasNoDuplicatesAndDoesNotRepeatAtBoundary() {
    SceneQueue queue = new SceneQueue(new Random(7));
    queue.configure(List.of("a", "b", "c"), true);
    Set<String> cycle = new HashSet<>();
    for (int i = 0; i < 3; i++) assertTrue(cycle.add(queue.step(1)));
    String last = queue.current();
    assertNotEquals(last, queue.step(1));
  }

  @Test
  public void backAndForwardFollowTheActualShuffleHistory() {
    SceneQueue queue = new SceneQueue(new Random(1));
    queue.configure(List.of("a", "b", "c"), true);
    String first = queue.step(1), second = queue.step(1);
    assertEquals(first, queue.step(-1));
    assertEquals(second, queue.step(1));
  }

  @Test
  public void exclusionsWinOverFavoritesAndEmptyIsSafe() {
    Map<String, String> scenes = new LinkedHashMap<>();
    scenes.put("a", "space");
    scenes.put("b", "cities");
    scenes.put("c", "space");
    assertEquals(
        List.of("b"),
        SceneQueue.allowed(scenes, Set.of("a", "b"), Set.of(), Set.of("space"), true));
    assertTrue(SceneQueue.allowed(scenes, Set.of("a"), Set.of("a"), Set.of(), true).isEmpty());
    SceneQueue queue = new SceneQueue(new Random(1));
    queue.configure(List.of(), true);
    assertNull(queue.step(1));
  }

  @Test
  public void changingEligibilityDropsExcludedHistory() {
    SceneQueue queue = new SceneQueue(new Random(1));
    queue.configure(List.of("a", "b"), false);
    queue.select("a");
    queue.step(1);
    queue.configure(List.of("a"), true);
    assertEquals("a", queue.step(1));
    assertEquals("a", queue.step(-1));
    assertFalse(queue.select("b"));
  }
}
