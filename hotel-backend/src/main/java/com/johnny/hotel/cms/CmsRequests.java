package com.johnny.hotel.cms;
import jakarta.validation.constraints.*;import lombok.*;import java.math.BigDecimal;import java.time.LocalDateTime;import java.util.List;
public final class CmsRequests {private CmsRequests(){}
 @Data public static class PageCreate{@NotBlank String slug;@NotBlank String locale;@NotBlank String title;String seoTitle,seoDescription;Long socialPreviewMediaId;}
 @Data public static class PageEdit{@NotBlank String title;String seoTitle,seoDescription;Long socialPreviewMediaId;@NotNull Integer expectedVersion;}
 @Data public static class SectionWrite{@NotBlank String sectionKey;@NotBlank String sectionType;@NotNull Integer sortOrder;@NotBlank String payload;String visibility="PUBLIC";@NotNull Integer expectedVersion=0;}
 @Data public static class Reorder{@NotEmpty List<Long> sectionIds;}
 @Data public static class NavigationWrite{@NotBlank String label;@NotBlank String locale;@NotBlank String targetType;String targetValue;@NotNull Integer sortOrder;Boolean visible=true;String openMode="SAME_WINDOW";String status="ACTIVE";}
 @Data public static class PromotionWrite{@NotBlank String title;String subtitle,description;@NotBlank String locale;Long coverMediaId;String galleryMediaIds;LocalDateTime startAt,endAt;String status="DRAFT";Integer priority=0;String ctaType,ctaTarget;Long pricingPolicyId;}
 @Data public static class LocationWrite{@NotBlank String locale;@NotBlank String hotelName;@NotBlank String address;@NotNull BigDecimal latitude,longitude;String phone,contact,displayMetadata;String status="DRAFT";}
 @Data public static class SceneWrite{@NotBlank String name,locale,sceneType,visibility,interactionMode;@NotNull Long mediaAssetId;Long previewMediaId;String cameraConfig;String status="DRAFT";}
 @Data public static class BindingWrite{@NotBlank String nodeKey;String floorReference;Long roomId;}
}
