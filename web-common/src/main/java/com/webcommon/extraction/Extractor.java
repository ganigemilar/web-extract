package com.webcommon.extraction;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.webcommon.BrowserSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Extractor {

  private Extractor() {}

  /**
   * Execute multiple extraction queries with per-query options.
   * Used by web-workflow where each query has its own attrs/html/limit.
   */
  public static List<ExtractionResult> extract(Page page, List<ExtractionQuery> queries) {
    List<ExtractionResult> results = new ArrayList<>();
    if (queries == null || queries.isEmpty()) {
      return results;
    }
    for (ExtractionQuery eq : queries) {
      results.add(extractOne(page, eq));
    }
    return results;
  }

  /**
   * Execute multiple queries with global options applied to all.
   * Used by web-extractor where CLI options apply globally to all queries.
   */
  public static List<ExtractionResult> extract(Page page, List<String> queries, ExtractionOptions globalOptions) {
    List<ExtractionResult> results = new ArrayList<>();
    if (queries == null || queries.isEmpty()) {
      return results;
    }
    for (String query : queries) {
      ExtractionQuery eq = globalOptions.toQuery(query);
      results.add(extractOne(page, eq));
    }
    return results;
  }

  /**
   * Execute a single extraction query.
   */
  public static ExtractionResult extractOne(Page page, ExtractionQuery query) {
    ExtractionResult result = new ExtractionResult(query.getQuery());

    try {
      Locator locator = page.locator(query.getQuery());
      int total = locator.count();
      int take = query.getLimit() > 0 ? Math.min(total, query.getLimit()) : total;

      List<Map<String, Object>> matches = new ArrayList<>();
      for (int i = 0; i < take; i++) {
        Locator el = locator.nth(i);
        String text = el.innerText().trim();

        Map<String, String> attrValues = null;
        if (!query.getAttrs().isEmpty()) {
          attrValues = new LinkedHashMap<>();
          for (String attr : query.getAttrs()) {
            attrValues.put(attr, el.getAttribute(attr));
          }
        }

        String html = query.isHtml() ? el.innerHTML() : null;
        matches.add(ExtractionResult.createMatch(text, attrValues, html));
      }

      result.setCount(total);
      result.setMatches(matches);
    } catch (PlaywrightException e) {
      result.setCount(0);
      result.setMatches(List.of());
      result.setError(BrowserSupport.firstLine(e.getMessage()));
    }
    return result;
  }

  /**
   * Execute a single query with options - convenience for web-extractor.
   */
  public static ExtractionResult extractOne(Page page, String query, ExtractionOptions options) {
    return extractOne(page, options.toQuery(query));
  }
}