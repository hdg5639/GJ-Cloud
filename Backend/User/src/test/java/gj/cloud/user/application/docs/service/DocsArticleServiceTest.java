package gj.cloud.user.application.docs.service;

import gj.cloud.user.application.docs.dto.DocsArticleUpsertRequest;
import gj.cloud.user.domain.docs.entity.DocsArticleEntity;
import gj.cloud.user.domain.docs.enums.DocsArticleStatus;
import gj.cloud.user.domain.docs.repository.DocsArticleRepository;
import gj.cloud.user.global.exception.UserException;
import gj.cloud.user.global.exception.enums.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocsArticleServiceTest {

    @Mock
    private DocsArticleRepository repository;

    private DocsArticleService service;

    @BeforeEach
    void setUp() {
        service = new DocsArticleService(repository);
    }

    @Test
    void createNormalizesSlugAndTagsAndKeepsArticleAsDraft() {
        when(repository.existsBySlug("instance-create")).thenReturn(false);
        when(repository.save(any(DocsArticleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("admin-id", request(" Instance Create ", List.of(" VM ", "VM", "시작")));

        assertThat(response.slug()).isEqualTo("instance-create");
        assertThat(response.tags()).containsExactly("VM", "시작");
        assertThat(response.status()).isEqualTo(DocsArticleStatus.DRAFT);
    }

    @Test
    void createRejectsDuplicateSlug() {
        when(repository.existsBySlug("instance-create")).thenReturn(true);

        assertThatThrownBy(() -> service.create("admin-id", request("instance-create", List.of())))
                .isInstanceOf(UserException.class)
                .extracting(cause -> ((UserException) cause).getErrorCode())
                .isEqualTo(UserErrorCode.DOCS_SLUG_ALREADY_EXISTS);
    }

    @Test
    void publishedListFiltersByCategoryAndSearchTerm() {
        DocsArticleEntity instance = article("instance-create", "인스턴스 생성", "인스턴스", List.of("VM", "시작"));
        DocsArticleEntity deploy = article("deploy-guide", "자동 배포", "배포", List.of("GitHub"));
        instance.publish();
        deploy.publish();
        when(repository.findAllByStatusOrderByFeaturedDescSortOrderAscPublishedAtDesc(DocsArticleStatus.PUBLISHED))
                .thenReturn(List.of(instance, deploy));

        var result = service.listPublished("vm", "인스턴스");

        assertThat(result).extracting(item -> item.slug()).containsExactly("instance-create");
    }

    private DocsArticleUpsertRequest request(String slug, List<String> tags) {
        return new DocsArticleUpsertRequest(
                slug,
                "인스턴스 생성하기",
                "처음부터 끝까지 인스턴스를 만드는 방법입니다.",
                "인스턴스",
                null,
                "## 시작하기\n\n본문",
                tags,
                true,
                0
        );
    }

    private DocsArticleEntity article(String slug, String title, String category, List<String> tags) {
        return DocsArticleEntity.create(
                "admin-id",
                slug,
                title,
                title + " 설명",
                category,
                null,
                "## 본문",
                tags,
                false,
                0
        );
    }
}
