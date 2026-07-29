package gj.cloud.ops.application.preview.blueprint.search;

import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintReindexResult;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchQuery;
import gj.cloud.ops.application.preview.blueprint.search.BlueprintSearchModels.BlueprintSearchResult;

public interface BlueprintSearchEngine {

    BlueprintSearchResult search(BlueprintSearchQuery query);

    BlueprintReindexResult reindex();
}
