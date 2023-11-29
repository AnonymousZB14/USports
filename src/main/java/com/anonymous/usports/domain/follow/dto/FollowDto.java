package com.anonymous.usports.domain.follow.dto;

import com.anonymous.usports.domain.follow.entity.FollowEntity;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowDto {

  private Long followId;
  private Long fromMemberId;
  private Long toMemberId;
  private LocalDateTime consentDate;

  public static FollowDto fromEntity(FollowEntity followEntity) {
    return FollowDto.builder()
        .followId(followEntity.getFollowId())
        .fromMemberId(followEntity.getFromMember().getMemberId())
        .toMemberId(followEntity.getToMember().getMemberId())
        .consentDate(followEntity.getConsentDate())
        .build();
  }
}
