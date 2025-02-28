// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.gitiles;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import java.io.IOException;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Convert exceptions into HTTP response. */
public class DefaultErrorHandlingFilter extends AbstractHttpFilter {
  private static final Logger log = LoggerFactory.getLogger(DefaultErrorHandlingFilter.class);

  /** HTTP header that indicates an error detail. */
  public static final String GITILES_ERROR = "X-Gitiles-Error";

  private Renderer renderer;

  public DefaultErrorHandlingFilter(Renderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    int status = -1;
    String message = null;
    try {
      chain.doFilter(req, res);
    } catch (GitilesRequestFailureException e) {
      res.setHeader(GITILES_ERROR, e.getReason().toString());
      status = e.getReason().getHttpStatusCode();
      message = e.getPublicErrorMessage();
    } catch (RepositoryNotFoundException e) {
      status = FailureReason.REPOSITORY_NOT_FOUND.getHttpStatusCode();
      message = FailureReason.REPOSITORY_NOT_FOUND.getMessage();
    } catch (AmbiguousObjectException e) {
      status = FailureReason.AMBIGUOUS_OBJECT.getHttpStatusCode();
      message = FailureReason.AMBIGUOUS_OBJECT.getMessage();
    } catch (ServiceMayNotContinueException e) {
      status = e.getStatusCode();
      message = e.getMessage();
    } catch (IOException | ServletException err) {
      log.warn("Internal server error", err);
      status = FailureReason.INTERNAL_SERVER_ERROR.getHttpStatusCode();
      message = FailureReason.INTERNAL_SERVER_ERROR.getMessage();
    }
    if (status != -1) {
      res.setStatus(status);
      renderHtml(req, res, "gitiles.error", ImmutableMap.of("title", message));
    }
  }

  protected void renderHtml(
      HttpServletRequest req, HttpServletResponse res, String templateName, Map<String, ?> soyData)
      throws IOException {
    renderer.render(req, res, templateName, startHtmlResponse(req, res, soyData));
  }

  private Map<String, ?> startHtmlResponse(
      HttpServletRequest req, HttpServletResponse res, Map<String, ?> soyData) throws IOException {
    res.setContentType(FormatType.HTML.getMimeType());
    res.setCharacterEncoding(UTF_8.name());
    BaseServlet.setNotCacheable(res);
    Map<String, Object> allData = BaseServlet.getData(req);
    allData.putAll(soyData);
    return allData;
  }
}
