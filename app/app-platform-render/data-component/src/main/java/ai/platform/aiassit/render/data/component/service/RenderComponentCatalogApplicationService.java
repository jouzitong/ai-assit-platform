package ai.platform.aiassit.render.data.component.service;

import ai.platform.aiassit.render.api.dto.RenderComponentCatalogQueryRequest;
import ai.platform.aiassit.render.api.dto.RenderComponentCatalogResponse;

/**
 * Read-only application service for the currently published Render component catalog.
 */
public interface RenderComponentCatalogApplicationService {

    RenderComponentCatalogResponse query(RenderComponentCatalogQueryRequest request);
}
