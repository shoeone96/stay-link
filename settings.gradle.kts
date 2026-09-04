rootProject.name = "stay-link"

// 모의 공급사 서버. 공급사마다 별도 프로세스로 띄워야 "A만 내렸을 때 B는 정상"을
// 재현할 수 있어서 프로젝트를 둘로 나눈다. 둘은 서로 의존하지 않고 공유 프로젝트도 없다.
// 루트 앱과도 project 의존을 두지 않아 프로덕션 classpath·bootJar 에 섞이지 않는다.
include("mock-supplier-a")
include("mock-supplier-b")
