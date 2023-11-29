package com.anonymous.usports.domain.follow.controller;

import com.anonymous.usports.domain.follow.dto.FollowListDto;
import com.anonymous.usports.domain.follow.dto.FollowResponse;
import com.anonymous.usports.domain.follow.service.FollowService;
import com.anonymous.usports.domain.member.dto.MemberDto;
import com.anonymous.usports.global.type.FollowDecisionType;
import com.anonymous.usports.global.type.FollowListType;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FollowController {

  private final FollowService followService;

  @ApiOperation("팔로우 신청/ 취소")
  @PostMapping("/follow/{toMemberId}")
  public ResponseEntity<FollowResponse> changeFollow(
      @PathVariable Long toMemberID,
      @AuthenticationPrincipal MemberDto member) {

    FollowResponse result = followService.changeFollow(member.getMemberId(), toMemberID);
    return ResponseEntity.ok(result);
  }

  @ApiOperation("타입별 팔로우 리스트 가져오기")
  @GetMapping("/follow/{type}")
  public ResponseEntity<FollowListDto> getFollowList(
      @PathVariable FollowListType type,
      @RequestParam("page") int page,
      @AuthenticationPrincipal MemberDto member) {
    FollowListDto followList = followService.getFollowList(type, page, member.getMemberId());
    return ResponseEntity.ok(followList);
  }

  @ApiOperation("팔로우 신청 수락 / 거절")
  @PostMapping("/follow/{followId}/manage")
  public ResponseEntity<FollowResponse> manageFollow(
      @PathVariable Long followId,
      @AuthenticationPrincipal MemberDto member,
      @RequestParam("decision") FollowDecisionType decision) {
    FollowResponse result = followService.manageFollow(followId, member.getMemberId(), decision);
    return ResponseEntity.ok(result);
  }


}
