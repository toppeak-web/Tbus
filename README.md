# 버스앱

> 자주 타는 버스를 선택해 홈 화면 위젯에서 확인하는 Android 앱

## 프로젝트 소개

정류장에 도착하는 모든 버스가 아니라, 사용자가 실제로 타는 버스만 선택해 위젯으로 관리하는 프로젝트입니다.

앱에서 정류장을 검색하고 자주 이용하는 버스를 고정하면, 홈 화면에서 버스 번호와 도착 예정 시간, 남은 정거장 수를 바로 확인할 수 있습니다.

## 주요 기능

- 정류장 검색 및 등록
- 정류장별 고정 버스 선택
- 홈 화면 RemoteViews 위젯
- 버스 도착 시간 및 남은 정거장 수 표시
- 다음 버스 도착정보 표시
- 위젯에서 즉시 새로고침
- 위젯별 정류장 선택 및 화면 설정
- 버스 노선의 전체 정류장 목록 확인
- 현재 운행 중인 버스 위치 표시

## 핵심 포인트: 원하는 버스만 위젯으로 관리

한 정류장에 여러 노선이 도착하면 작은 위젯에 모든 정보를 표시하기 어렵습니다.

이 앱은 사용자가 정류장별로 자주 타는 버스를 직접 고정하도록 구성했습니다.

```text
정류장 검색 및 등록
        ↓
운행 버스 확인
        ↓
자주 타는 버스 고정
        ↓
홈 화면에서 고정 버스만 확인
```

## API 호출 방식

카카오 지도 버스 서비스의 내부 정류장 API를 OkHttp로 호출합니다.

```http
GET https://map.kakao.com/bus/stop.json?busstopid={BUS_STOP_ID}
```

예를 들어 서울역 600번 정류장의 표시번호가 `02694`인 경우, 검색 결과의 API 식별자 `BS151150`을 사용해 호출합니다.

```text
정류장 번호: 02694
API 식별자: BS151150
노선: 600
```

```http
GET https://map.kakao.com/bus/stop.json?busstopid=BS151150
```

응답에는 정류장 정보와 노선별 도착정보가 포함됩니다.

```kotlin
val request = Request.Builder()
    .url("https://map.kakao.com/bus/stop.json?busstopid=$busStopId")
    .get()
    .build()

client.newCall(request).execute().use { response ->
    val body = response.body?.string().orEmpty()
    val jsonText = stripJsonp(body)
    val root = JSONObject(jsonText)
    return parseBusStopInfo(root, busStopId)
}
```

응답 구조는 다음과 같은 형태를 가정합니다.

```json
{
  "name": "서울역",
  "id": "BS151150",
  "lines": [
    {
      "id": "600",
      "name": "600",
      "arrival": {
        "direction": "온수동 방향",
        "arrivalTime": 420,
        "busStopCount": 3,
        "arrivalTime2": 1860,
        "busStopCount2": 14
      }
    }
  ]
}
```


## 주요 구현 파일

| 파일 | 역할 |
| --- | --- |
| `KakaoBusApi.kt` | 정류장 검색, API 호출, JSONP 제거, JSON 파싱 |
| `BusWidgetProvider.kt` | 위젯 생성, 새로고침, 도착정보 표시 |
| `WidgetConfigActivity.kt` | 위젯별 정류장 및 표시 설정 |
| `BusPinConfigActivity.kt` | 정류장별 고정 버스 선택 |
| `BusRouteStopsActivity.kt` | 노선 정류장과 버스 위치 표시 |

## 화면 이미지


| 정류장 검색 | 고정 버스 선택 | 홈 화면 위젯 |
| --- | --- | --- |
| <img width="417" height="863" alt="Screenshot 2026-11-12 17-21-56" src="https://github.com/user-attachments/assets/e0273486-df9d-4469-afcb-46f969c939ee" /> | <img width="417" height="863" alt="Screenshot 2026-11-12 17-22-16" src="https://github.com/user-attachments/assets/d99a76b3-3c50-45be-b539-a5ca4ecf1d83" /> | <img width="417" height="863" alt="Screenshot 2026-11-12 17-23-36" src="https://github.com/user-attachments/assets/1e96c54f-ec22-4497-8852-5cb1d388c92b" /> |







## 참고

버스 데이터는 카카오 지도 버스 서비스의 내부 API 응답을 기반으로 처리합니다. 공식 공개 API가 아니므로 응답 형식이나 제공 여부가 변경될 수 있습니다.
