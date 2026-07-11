package com.blog.content.service;

import com.blog.content.entity.OssResource;
import com.blog.content.mapper.AdvertisementMapper;
import com.blog.content.mapper.CarouselMapper;
import com.blog.content.mapper.FriendLinkMapper;
import com.blog.content.mapper.OssResourceMapper;
import com.blog.content.mapper.PostContentMapper;
import com.blog.content.mapper.PostMapper;
import com.blog.content.mapper.TopicMapper;
import com.blog.infra.oss.StorageFacade;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaReferenceServiceTest {

    private final PostMapper postMapper = mock(PostMapper.class);
    private final PostContentMapper postContentMapper = mock(PostContentMapper.class);
    private final OssResourceMapper ossResourceMapper = mock(OssResourceMapper.class);
    private final TopicMapper topicMapper = mock(TopicMapper.class);
    private final CarouselMapper carouselMapper = mock(CarouselMapper.class);
    private final FriendLinkMapper friendLinkMapper = mock(FriendLinkMapper.class);
    private final AdvertisementMapper advertisementMapper = mock(AdvertisementMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final OssResourceService ossResourceService = mock(OssResourceService.class);
    private final StorageFacade storageFacade = mock(StorageFacade.class);

    @Test
    void shouldUseSameReferenceKeyForLegacyAndCurrentLocalUploadUrls() {
        MediaReferenceService service = buildService();

        assertThat(service.toReferenceKey("http://localhost:9091/uploads/2026/04/a.png"))
            .isEqualTo("key:2026/04/a.png");
        assertThat(service.toReferenceKey("https://example.com/uploads/2026/04/a.png?x-oss-process=image/resize"))
            .isEqualTo("key:2026/04/a.png");
    }

    @Test
    void shouldCountResourceAsReferencedWhenStoredUrlUsesLegacyDomain() {
        MediaReferenceService service = buildService();
        OssResource resource = new OssResource();
        resource.setUrl("http://localhost:9091/uploads/2026/04/a.png");

        when(ossResourceMapper.selectList(any())).thenReturn(List.of(resource));

        long count = service.countUnreferenced(Set.of("https://example.com/uploads/2026/04/a.png"));

        assertThat(count).isZero();
    }

    @Test
    void shouldBuildTopicReferenceDetailsWhenNoPostExists() {
        MediaReferenceService service = buildService();
        when(postMapper.selectActivePostSummaries()).thenReturn(List.of());
        when(topicMapper.selectPublishedTopicSummaries()).thenReturn(List.of(Map.of(
            "id", 1L,
            "title", "专题",
            "cover", "https://example.com/uploads/topic.png"
        )));

        Map<String, List<com.blog.content.dto.MediaVO.ReferenceDetail>> details = service.getReferenceDetailsMap();

        assertThat(details).containsKey("https://example.com/uploads/topic.png");
    }

    @Test
    void shouldTreatDisabledCarouselImageAsReferencedConfiguration() {
        MediaReferenceService service = buildService();
        OssResource resource = new OssResource();
        resource.setUrl("http://localhost:9091/uploads/images/2026/05/carousel.webp");

        when(carouselMapper.selectConfiguredImageUrls()).thenReturn(List.of("http://localhost:9091/uploads/images/2026/05/carousel.webp"));
        when(ossResourceMapper.selectList(any())).thenReturn(List.of(resource));

        long count = service.countUnreferenced();

        assertThat(count).isZero();
    }

    @Test
    void shouldExtractAdvertisementImagesFromDirectUrlAndHtmlContent() {
        MediaReferenceService service = buildService();
        when(postMapper.selectActivePostSummaries()).thenReturn(List.of());
        when(advertisementMapper.selectImageAdSummaries()).thenReturn(List.of(
            Map.of("id", 1L, "title", "直接地址", "content", "http://localhost:9091/uploads/images/ad-direct.webp"),
            Map.of("id", 2L, "title", "内嵌图片", "content", "<a href=\"#\"><img src=\"http://localhost:9091/uploads/images/ad-html.webp\"></a>"),
            Map.of("id", 3L, "title", "背景图片", "content", "<div style=\"background-image:url('/uploads/images/ad-bg.webp')\"></div>")
        ));

        Map<String, List<com.blog.content.dto.MediaVO.ReferenceDetail>> details = service.getReferenceDetailsMap();

        assertThat(details).containsKeys(
            "http://localhost:9091/uploads/images/ad-direct.webp",
            "http://localhost:9091/uploads/images/ad-html.webp",
            "/uploads/images/ad-bg.webp"
        );
    }

    @Test
    void shouldCountAdvertisementImagesAsReferencedWhenCleaningCandidates() {
        MediaReferenceService service = buildService();
        OssResource directResource = new OssResource();
        directResource.setUrl("http://localhost:9091/uploads/images/ad-direct.webp");
        OssResource htmlResource = new OssResource();
        htmlResource.setUrl("http://localhost:9091/uploads/images/ad-html.webp");
        OssResource cssResource = new OssResource();
        cssResource.setUrl("http://localhost:9091/uploads/images/ad-bg.webp");

        when(advertisementMapper.selectImageContentUrls()).thenReturn(List.of(
            "http://localhost:9091/uploads/images/ad-direct.webp",
            "<img src=\"http://localhost:9091/uploads/images/ad-html.webp\">",
            "<div style=\"background:url('/uploads/images/ad-bg.webp')\"></div>"
        ));
        when(ossResourceMapper.selectList(any())).thenReturn(List.of(directResource, htmlResource, cssResource));

        long count = service.countUnreferenced();

        assertThat(count).isZero();
    }

    private MediaReferenceService buildService() {
        return new MediaReferenceService(
            postMapper,
            postContentMapper,
            ossResourceMapper,
            topicMapper,
            carouselMapper,
            friendLinkMapper,
            advertisementMapper,
            redisTemplate,
            ossResourceService,
            storageFacade
        );
    }
}
