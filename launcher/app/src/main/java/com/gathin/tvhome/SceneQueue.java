package com.gathin.tvhome;

import java.util.*;

/** A shuffled bag avoids repeats until every eligible scene has played; Back follows history. */
final class SceneQueue {
  private final Random random;
  private List<String> eligible = List.of();
  private final List<String> bag = new ArrayList<>(), history = new ArrayList<>();
  private boolean shuffle;
  private int cursor = -1, orderedIndex;

  SceneQueue(Random random) {
    this.random = random;
  }

  void configure(List<String> ids, boolean shuffled) {
    List<String> unique = new ArrayList<>(new LinkedHashSet<>(ids));
    if (eligible.equals(unique) && shuffle == shuffled) return;
    String current = current();
    eligible = unique;
    shuffle = shuffled;
    bag.clear();
    history.clear();
    cursor = -1;
    orderedIndex = 0;
    if (eligible.contains(current)) select(current);
  }

  String current() {
    return cursor < 0 ? null : history.get(cursor);
  }

  boolean select(String id) {
    if (!eligible.contains(id)) return false;
    append(id);
    if (shuffle && bag.isEmpty()) {
      bag.addAll(eligible);
      Collections.shuffle(bag, random);
    }
    bag.remove(id);
    orderedIndex = (eligible.indexOf(id) + 1) % eligible.size();
    return true;
  }

  String step(int direction) {
    if (eligible.isEmpty()) return null;
    if (direction < 0 && cursor > 0) return history.get(--cursor);
    if (direction < 0 && cursor == 0) return current();
    if (cursor + 1 < history.size()) return history.get(++cursor);
    String next;
    if (shuffle) {
      if (bag.isEmpty()) {
        bag.addAll(eligible);
        Collections.shuffle(bag, random);
        if (bag.size() > 1 && bag.get(0).equals(current())) Collections.swap(bag, 0, 1);
      }
      next = bag.remove(0);
    } else {
      next = eligible.get(orderedIndex++ % eligible.size());
    }
    append(next);
    return next;
  }

  private void append(String id) {
    while (history.size() > cursor + 1) history.remove(history.size() - 1);
    if (id.equals(current())) return;
    history.add(id);
    cursor++;
    if (history.size() > 256) {
      history.remove(0);
      cursor--;
    }
  }

  static List<String> allowed(
      Map<String, String> categories,
      Set<String> favorites,
      Set<String> excluded,
      Set<String> excludedCategories,
      boolean favoritesOnly) {
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, String> scene : categories.entrySet())
      if (!excluded.contains(scene.getKey())
          && !excludedCategories.contains(scene.getValue())
          && (!favoritesOnly || favorites.contains(scene.getKey()))) result.add(scene.getKey());
    return result;
  }
}
