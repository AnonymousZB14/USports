package com.anonymous.usports.domain.member.service.impl;

import com.anonymous.usports.domain.member.dto.MailResponse;
import com.anonymous.usports.domain.member.dto.MemberDto;
import com.anonymous.usports.domain.member.dto.MemberLogin;
import com.anonymous.usports.domain.member.dto.MemberRegister;
import com.anonymous.usports.domain.member.dto.MemberUpdate;
import com.anonymous.usports.domain.member.dto.MemberWithdraw;
import com.anonymous.usports.domain.member.dto.PasswordLostResponse;
import com.anonymous.usports.domain.member.dto.PasswordUpdate;
import com.anonymous.usports.domain.member.entity.InterestedSportsEntity;
import com.anonymous.usports.domain.member.entity.MemberEntity;
import com.anonymous.usports.domain.member.repository.InterestedSportsRepository;
import com.anonymous.usports.domain.member.repository.MemberRepository;
import com.anonymous.usports.domain.member.service.MailService;
import com.anonymous.usports.domain.member.service.MemberService;
import com.anonymous.usports.domain.sports.dto.SportsDto;
import com.anonymous.usports.domain.sports.repository.SportsRepository;
import com.anonymous.usports.global.constant.MailConstant;
import com.anonymous.usports.global.constant.ResponseConstant;
import com.anonymous.usports.global.constant.TokenConstant;
import com.anonymous.usports.global.exception.ErrorCode;
import com.anonymous.usports.global.exception.MemberException;
import com.anonymous.usports.global.exception.MyException;
import com.anonymous.usports.global.redis.auth.repository.AuthRedisRepository;
import com.anonymous.usports.global.redis.token.repository.TokenRepository;
import com.anonymous.usports.global.type.LoginBy;
import com.anonymous.usports.global.type.Role;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService, UserDetailsService {

    private final MemberRepository memberRepository;
    private final InterestedSportsRepository interestedSportsRepository;
    private final SportsRepository sportsRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRepository tokenRepository;
    private final AuthRedisRepository authRedisRepository;
    private final MailService mailService;

    private void checkDuplication(String accountName, String email){
        if (memberRepository.existsByAccountName(accountName)) {
            throw new MemberException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }

        if (memberRepository.existsByEmail(email)) {
            throw new MemberException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

    }

    private MemberRegister.Response saveMember(MemberRegister.Request request) {

        request.setPassword(passwordEncoder.encode(request.getPassword()));

        memberRepository.save(MemberRegister.Request.toEntity(request, LoginBy.USPORTS));

        mailService.sendEmailAuthMail(request.getEmail());

        return MemberRegister.Response.fromEntity(
                MemberRegister.Request.toEntity(request, LoginBy.USPORTS), MailConstant.AUTH_EMAIL_SEND
        );
    }

    @Override
    public MemberRegister.Response registerMember(MemberRegister.Request request) {

        checkDuplication(request.getAccountName(), request.getEmail());

        return saveMember(request);
    }

    @Override
    public MemberDto loginMember(MemberLogin.Request request) {

        MemberDto memberDto = (MemberDto) loadUserByUsername(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), memberDto.getPassword())) {
            throw new MemberException(ErrorCode.PASSWORD_UNMATCH);
        }

        return memberDto;
    }

    @Override
    public String logoutMember(String accessToken, String email) {

        boolean result = tokenRepository.deleteToken(email);

        if(!result) return TokenConstant.LOGOUT_NOT_SUCCESSFUL;

        tokenRepository.addBlackListAccessToken(accessToken);

        return TokenConstant.LOGOUT_SUCCESSFUL;
    }

    private MemberEntity passwordCheckAndGetMember(MemberDto memberDto, Long memberId, String password) {

        if (!Role.ADMIN.equals(memberDto.getRole()) && !memberId.equals(memberDto.getMemberId())) {
            throw new MemberException(ErrorCode.MEMBER_ID_UNMATCH);
        }

        MemberEntity memberEntity = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(password, memberEntity.getPassword()))
            throw new MemberException(ErrorCode.PASSWORD_UNMATCH);

        return memberEntity;
    }

    @Override
    public MemberWithdraw.Response deleteMember(MemberDto memberDto, MemberWithdraw.Request request, Long memberId) {

        memberRepository.delete(passwordCheckAndGetMember(memberDto, memberId, request.getPassword()));

        return new MemberWithdraw.Response(ResponseConstant.MEMBER_DELETE_SUCCESS);
    }


    private MemberEntity checkDuplicationUpdate(MemberDto memberDto, MemberUpdate.Request request){

        MemberEntity memberEntity = memberRepository.findById(memberDto.getMemberId())
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND));

        if (!memberEntity.getAccountName().equals(request.getAccountName())) {
            if (memberRepository.existsByAccountName(request.getAccountName())) {
                throw new MemberException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
            }
        }

        if (!memberEntity.getEmail().equals(memberDto.getEmail())) {
            if (memberRepository.existsByEmail(memberDto.getEmail())) {
                throw new MemberException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }

        return memberEntity;
    }

  // 관심 운동이 있을 경우, 수정을 했을 상황을 대비해, 이미 저장되어 있는 데이터는 다 삭제하고 다시 저장하기
  private List<InterestedSportsEntity> saveInterestedSportsEntities(List<Long> allSelectedSports,
      MemberEntity memberEntity) {

    interestedSportsRepository.deleteAllByMemberEntity(memberEntity);

    return interestedSportsRepository.saveAll(
        allSelectedSports.stream()
            .map(id ->
                InterestedSportsEntity.builder()
                    .sports(sportsRepository.findById(id)
                        .orElseThrow(() -> new MyException(ErrorCode.SPORTS_NOT_FOUND)))
                    .memberEntity(memberEntity)
                    .build())
            .collect(Collectors.toList()));
  }

  @Override
  @Transactional
  public MemberUpdate.Response updateMember(MemberUpdate.Request request, MemberDto memberDto,
      Long memberId) {

    if (!Role.ADMIN.equals(memberDto.getRole()) && !memberId.equals(memberDto.getMemberId())) {
      throw new MemberException(ErrorCode.MEMBER_ID_UNMATCH);
    }

    // 닉네임, 이메일를 수정 할 때, 겹치지 않게
    // 하는 김에 MemberEntity 가지고 오기
    MemberEntity memberEntity = checkDuplicationUpdate(memberDto, request);

    if (Role.UNAUTH.equals(memberDto.getRole()) && memberDto.getEmailAuthAt() == null) {
      int redisEmailAuthNumber = authRedisRepository.getEmailAuthNumber(memberDto.getEmail());

      if (redisEmailAuthNumber != request.getEmailAuthNumber()) {
        throw new MemberException(ErrorCode.EMAIL_AUTH_NUMBER_UNMATCH);
      }

      memberEntity.setEmailAuthAt(LocalDateTime.now());
    }

    // 관심 운동이 아예 없으면 안 된다
    if (request.getInterestedSportsList().isEmpty()) {
      throw new MemberException(ErrorCode.NEED_AT_LEAST_ONE_SPORTS);
    }

    // 맴버 entity 수정
    memberEntity.updateMember(request);

    // 관심 운동 삭제 후 저장
    List<InterestedSportsEntity> savedInterestedSportsList =
        saveInterestedSportsEntities(request.getInterestedSportsList(), memberEntity);

    MemberUpdate.Response response = MemberUpdate.Response.fromEntity(memberEntity);
    response.setInterestedSportsList(
        savedInterestedSportsList.stream()
            .map(InterestedSportsEntity::getSports)
            .map(SportsDto::new)
            .collect(Collectors.toList())
    );

    return response;
  }

    @Override
    public PasswordUpdate.Response updatePassword(PasswordUpdate.Request request, Long id, MemberDto memberDto) {

        // 기존 비밀번호와 일치하는지 확인
        MemberEntity memberEntity = passwordCheckAndGetMember(memberDto, id, request.getCurrentPassword());

        if (!request.getNewPassword().equals(request.getNewPasswordCheck())) {
            throw new MemberException(ErrorCode.NEW_PASSWORD_UNMATCH);
        }

        memberEntity.setPassword(passwordEncoder.encode(request.getNewPassword()));

        memberRepository.save(memberEntity);

        return new PasswordUpdate.Response(ResponseConstant.PASSWORD_CHANGE_SUCCESS);
    }

    @Override
    public PasswordLostResponse.Response lostPassword(PasswordLostResponse.Request request) {
        MemberEntity memberEntity = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND));

        if (!memberEntity.getPhoneNumber().equals(request.getPhoneNumber())) {
            throw new MemberException(ErrorCode.PHONE_NUMBER_UNMATCH);
        }

        if (!memberEntity.getName().equals(request.getName())) {
            throw new MemberException(ErrorCode.NAME_UNMATCH);
        }

        String tempPassword = mailService.sendTempPassword(request.getEmail());


        memberEntity.setPassword(passwordEncoder.encode(tempPassword));

        memberRepository.save(memberEntity);

        return new PasswordLostResponse.Response(request.getEmail() + MailConstant.TEMP_PASSWORD_SUCCESSFULLY_SENT);
    }

    @Override
    public MailResponse resendEmailAuth(MemberDto memberDto, Long memberId) {

        if (!memberId.equals(memberDto.getMemberId()))
            throw new MemberException(ErrorCode.MEMBER_ID_UNMATCH);

        if (!memberRepository.existsById(memberId))
            throw new MemberException(ErrorCode.MEMBER_NOT_FOUND);

        mailService.sendEmailAuthMail(memberDto.getEmail());

        return new MailResponse(MailConstant.AUTH_EMAIL_SEND);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return MemberDto.fromEntity(memberRepository.findByEmail(username)
                .orElseThrow(() -> new MemberException(ErrorCode.MEMBER_NOT_FOUND)));
    }
}
