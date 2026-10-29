import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import type { StatusLog } from '../../types/index.ts';
import { STATUS_LOG_TEXT, timelineOf } from './statusLogView.ts';

const START_AT = '2026-10-24T13:00:00+09:00';
const APPLICANT = '김담당';

const log = (changedAt: string, statusType: StatusLog['statusType'], newStatus: string, actor: StatusLog['actor'] = 'SYSTEM', detail: string | null = null): StatusLog => ({
  statusType,
  prevStatus: null,
  newStatus,
  changedAt,
  detail,
  actor,
});

/** 승인 → 오픈 예약 → 호스팅 시작 → 입장 시작(시작 − 60분) */
const OPENED_LOGS: StatusLog[] = [
  log('2026-10-23T09:30:00+09:00', 'APPLICATION', 'APPROVED', 'OPERATOR'),
  log('2026-10-23T09:30:00+09:00', 'EVENT', 'SCHEDULED'),
  log('2026-10-24T11:00:00+09:00', 'HOSTING', 'HOSTING'),
  log('2026-10-24T12:00:00+09:00', 'EVENT', 'OPEN'),
];

describe('timelineOf: 이벤트 시작 항목', () => {
  test('시작 전: 입장 시작 다음에 예정 항목(회색 · 예정 칩 · 처리자 "예정")', () => {
    const items = timelineOf(OPENED_LOGS, START_AT, new Date('2026-10-24T12:59:59+09:00'), APPLICANT);
    assert.deepEqual(items.map((l) => l.title), ['운영자 승인', '오픈 예약', '호스팅 시작', '입장 시작', '이벤트 시작']);
    const start = items[4];
    assert.equal(start.at, '2026.10.24 13:00');
    assert.equal(start.actor, '예정');
    assert.deepEqual(start.status, { label: '예정', tone: 'grey' });
    assert.equal(start.description, '이벤트 시작 시각에 쿠폰 발급이 시작됩니다.');
    assert.equal(start.planned, true);
  });

  test('시작 시각부터: 진행 중 + 발급 시작 문구', () => {
    const start = timelineOf(OPENED_LOGS, START_AT, new Date(START_AT), APPLICANT).at(-1);
    assert.equal(start?.title, '이벤트 시작');
    assert.equal(start?.actor, '자동 처리');
    assert.equal(start?.status.label, '진행 중');
    assert.equal(start?.description, '쿠폰 발급이 시작되었습니다.');
    assert.equal(start?.planned, false);
  });

  test('EVENT OPEN 기록이 없으면(반려 · 입장 전 실패) 이벤트 시작을 넣지 않는다', () => {
    const rejected = [log('2026-10-23T09:30:00+09:00', 'APPLICATION', 'REJECTED', 'OPERATOR', '배너 이미지가 정책에 맞지 않습니다.')];
    assert.deepEqual(timelineOf(rejected, START_AT, new Date(START_AT), APPLICANT).map((l) => l.title), ['운영자 반려']);
    const openFailed = [...OPENED_LOGS.slice(0, 3), log('2026-10-24T12:00:00+09:00', 'EVENT', 'OPEN_FAILED', 'SYSTEM', 'Pod 준비 시간 초과')];
    assert.ok(!timelineOf(openFailed, START_AT, new Date(START_AT), APPLICANT).some((l) => l.title === '이벤트 시작'));
  });

  test('같은 시각이면 로그 뒤 · 시각순 정렬', () => {
    const sameTime = [...OPENED_LOGS, log(START_AT, 'HOSTING', 'END_FAILED')];
    assert.deepEqual(timelineOf(sameTime, START_AT, new Date(START_AT), APPLICANT).slice(-2).map((l) => l.title), ['종료 처리 확인 중', '이벤트 시작']);
    const later = [...OPENED_LOGS, log('2026-10-24T15:00:00+09:00', 'EVENT', 'EXPIRED')];
    assert.deepEqual(timelineOf(later, START_AT, new Date('2026-10-24T16:00:00+09:00'), APPLICANT).slice(-2).map((l) => l.title), ['이벤트 시작', '이벤트 만료']);
  });
});

describe('timelineOf: 문구 · 입장 시각', () => {
  test('호스팅 시작 설명에 실제 입장 시각(시작 − 60분, 한국 시각)', () => {
    const hosting = timelineOf(OPENED_LOGS, START_AT, new Date(START_AT), APPLICANT)[2];
    // TEST-LEAD 원래: assert.equal(hosting.description, '호스팅 시작 시각이 되어 이벤트 페이지(대기방)를 열었습니다. 입장은 10.24 12:00에 열립니다.');
    assert.equal(hosting.description, '호스팅 시작 시각이 되어 이벤트 페이지(대기방)를 열었습니다. 입장은 10.24 12:55에 열립니다.'); // TEST-LEAD
    // 자정을 넘으면 전날 날짜
    // TEST-LEAD 원래: const early = timelineOf([log('2026-10-24T23:00:00+09:00', 'HOSTING', 'HOSTING')], '2026-10-25T00:30:00+09:00', new Date(START_AT), APPLICANT);
    const early = timelineOf([log('2026-10-24T23:00:00+09:00', 'HOSTING', 'HOSTING')], '2026-10-25T00:03:00+09:00', new Date(START_AT), APPLICANT); // TEST-LEAD
    // TEST-LEAD 원래: assert.equal(early[0].description, '호스팅 시작 시각이 되어 이벤트 페이지(대기방)를 열었습니다. 입장은 10.24 23:30에 열립니다.');
    assert.equal(early[0].description, '호스팅 시작 시각이 되어 이벤트 페이지(대기방)를 열었습니다. 입장은 10.24 23:58에 열립니다.'); // TEST-LEAD
  });

  test('처리 주체 · 반려 사유 · 모르는 상태', () => {
    const items = timelineOf(
      [
        log('2026-10-22T10:00:00+09:00', 'APPLICATION', 'APPLYING', 'TENANT'),
        log('2026-10-23T09:30:00+09:00', 'APPLICATION', 'REJECTED', 'OPERATOR', '배너 이미지가 정책에 맞지 않습니다.'),
        log('2026-10-23T10:00:00+09:00', 'HOSTING', 'SOMETHING_NEW'),
      ],
      START_AT,
      new Date(START_AT),
      APPLICANT,
    );
    assert.equal(items[0].actor, '김담당 (테넌트 관리자)');
    assert.equal(items[0].title, '신청 접수');
    assert.equal(items[1].actor, '플랫폼 운영자');
    assert.deepEqual(items[1].detail, { label: '반려 사유', text: '배너 이미지가 정책에 맞지 않습니다.', rejected: true });
    assert.equal(items[2].title, 'SOMETHING_NEW');
    assert.equal(items[2].description, '');
  });
});

describe('실패 기록은 확인 중', () => {
  // 고객 화면은 실패 대신 확인 중(2026-10-29). 호스팅 · 이벤트 기록의 원인은 서버가 내리지 않는다(detail null)
  test('배포 · 호스팅 시작 · 입장 준비 · 종료 실패는 확인 중 칩(황토) + 운영팀 안내, 원인 칸 없음', () => {
    const failures = [
      log('2026-10-23T09:40:00+09:00', 'HOSTING', 'DEPLOY_FAILED'),
      log('2026-10-24T11:00:00+09:00', 'HOSTING', 'HOSTING_FAILED'),
      log('2026-10-24T12:00:00+09:00', 'EVENT', 'OPEN_FAILED'),
      log('2026-10-24T16:00:00+09:00', 'HOSTING', 'END_FAILED'),
    ];
    const items = timelineOf(failures, START_AT, new Date(START_AT), APPLICANT);
    assert.deepEqual(
      items.map((l) => l.title),
      ['배포 확인 중', '호스팅 시작 확인 중', '입장 준비 확인 중', '종료 처리 확인 중'],
    );
    for (const item of items) {
      assert.deepEqual(item.status, { label: '확인 중', tone: 'amber' });
      assert.equal(item.description, '운영팀이 확인하고 있습니다.');
      assert.equal(item.detail, null);
    }
  });

  test('호스팅 · 이벤트 문구에 실패 · 오류 같은 말이 없다', () => {
    for (const type of ['HOSTING', 'EVENT'] as const) {
      for (const [status, text] of Object.entries(STATUS_LOG_TEXT[type])) {
        assert.doesNotMatch(`${text.title} ${text.description}`, /실패|오류|지연/, `${type} ${status}`);
      }
    }
  });

  test('신청 기록의 반려 사유 · 신청 실패 원인은 그대로 보여 준다', () => {
    const [rejected] = timelineOf([log('2026-10-23T09:30:00+09:00', 'APPLICATION', 'REJECTED', 'OPERATOR', '기간이 너무 짧습니다')], START_AT, new Date(START_AT), APPLICANT);
    assert.deepEqual(rejected.detail, { label: '반려 사유', text: '기간이 너무 짧습니다', rejected: true });
    const [failed] = timelineOf([log('2026-10-23T09:30:00+09:00', 'APPLICATION', 'APPLY_FAILED', 'SYSTEM', '배너 업로드 실패')], START_AT, new Date(START_AT), APPLICANT);
    assert.equal(failed.title, '신청 실패');
    assert.deepEqual(failed.detail, { label: '원인', text: '배너 업로드 실패', rejected: false });
  });
});
