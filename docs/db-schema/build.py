import re, html as H
T='/Users/won/.claude/skills/toss-design/template.html'
head=open(T,encoding='utf-8').read().split('</style>')[0]+'</style>\n</head>\n'
head=head.replace('{{문서 제목}}','stay-link DB 스키마')

import sys, json
sys.path.insert(0,'/Users/won/.claude/skills/toss-design/scripts')
from er_schema import render
import os; ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
svgB=render(json.load(open(f'{ROOT}/docs/db-schema/current.json',encoding='utf-8')))

body=f'''<body>
<div id="bar"></div>

<div class="sheet wide">

  <header class="cover">
    <div class="cov">
      <span class="badge"><span class="d"></span>stay-link · DB SCHEMA · SSOT</span>
      <h1 class="title">stay-link<br><span class="hl">DB 스키마</span></h1>
      <p class="lede">테이블·컬럼·제약의 <b>단일 원본 문서</b>입니다. <span class="term">schema.sql</span>·엔티티가 바뀌면 같은 커밋에서 이 문서의 다이어그램·설명·변경 이력을 함께 갱신합니다. 현재 테이블은 F1 property-mapping의 2개입니다.</p>
      <div class="chips">
        <div class="chip"><span class="k">TABLES</span><span class="v">property · room</span></div>
        <div class="chip"><span class="k">DB</span><span class="v">MySQL 8.4 · schema.sql + ddl-auto validate</span></div>
        <div class="chip"><span class="k">TEST</span><span class="v">H2 create-drop (FK 미생성)</span></div>
        <div class="chip"><span class="k">UPDATED</span><span class="v">2026-09-04</span></div>
      </div>
    </div>
  </header>

  <nav class="toc">
    <h2>목차</h2>
    <ol class="toc-index">
      <li><a href="#s1">현재 스키마<span class="hint">property · room</span></a></li>
      <li><a href="#s3">제약과 관계의 의미</a></li>
      <li><a href="#s4">코드가 겹칠 때의 저장 예시<span class="hint">예시</span></a></li>
      <li><a href="#s5">변경 이력</a></li>
    </ol>
  </nav>

  <main>

  <section id="s1" class="fade">
    <div class="sec-head">
      <div class="sec-num">1</div>
      <div><p class="sec-eyebrow">CURRENT</p><h2 class="sec">현재 스키마</h2></div>
    </div>
    <p class="lead">property가 공급사 코드를 내부 id로 바꾸고, room이 그 id 아래에 객실 유형 코드를 매답니다. room은 공급사의 판매 단위(객실 유형)이며 물리 객실이 아닙니다.</p>
    <figure>
      <div class="fig-box" data-fig="ER · CURRENT">{svgB}</div>
      <figcaption><b>그림 1.</b> 현재 스키마. KEY 열의 PK·FK·UQ는 해당 컬럼이 참여하는 제약. room의 공급사는 property_id가 가리키는 property 행에서 읽는다.</figcaption>
    </figure>
  </section>

  <section id="s3" class="fade">
    <div class="sec-head">
      <div class="sec-num">2</div>
      <div><p class="sec-eyebrow">CONSTRAINTS</p><h2 class="sec">제약과 관계의 의미</h2></div>
    </div>
    <div class="tbl-wrap">
      <table>
        <thead><tr><th>제약</th><th>대상</th><th>지키는 규칙</th></tr></thead>
        <tbody>
          <tr><td><span class="term">uq_property_supplier_code</span></td><td>property (supplier, supplier_property_code)</td><td>"공급사 X의 숙소 코드 Y"는 한 행뿐. 같은 공급사 상품이 언제나 같은 내부 id를 갖는다는 원칙(D4)의 DB 측 보장. 공급사 코드가 서로 겹치는 문제는 여기서 해결된다.</td></tr>
          <tr><td><span class="term">uq_room_property_code</span></td><td>room (property_id, supplier_room_code)</td><td>"숙소 P 안에서 객실 유형 코드 Z"는 한 행뿐. property_id가 (supplier, supplier_property_code)로 유일한 행을 가리키므로 공급사 구분은 이미 키에 포함된다 (D-F1-9). 다른 숙소의 같은 코드는 허용된다. 메모리 검사는 두지 않는다 (Aggregate가 다르므로, D-F1-4).</td></tr>
          <tr><td><span class="term">fk_room_property</span></td><td>room.property_id → property.id</td><td>소속 숙소가 없는 객실 유형은 존재할 수 없다. 로컬 MySQL(validate)에서만 생성되고 테스트 H2(create-drop)에서는 만들어지지 않는다 (D-F1-1).</td></tr>
        </tbody>
      </table>
    </div>
    <div class="note warn"><span class="tag">운영 주의</span><span class="term">schema.sql</span>은 <span class="term">CREATE TABLE IF NOT EXISTS</span> + <span class="term">sql.init.mode: always</span>로 매 기동마다 실행된다. 컬럼 추가처럼 <strong>기존 테이블을 바꾸는 변경은 재실행되지 않으므로</strong> 로컬 컨테이너를 <span class="term">docker compose down</span>으로 지우고 다시 띄워야 validate가 통과한다 (2026-09-04 컬럼 추가 실험 중 실제 재현). 첫 ALTER가 필요해지는 시점에 마이그레이션 도구를 재검토한다 (D-F1-2).</div>
  </section>

  <section id="s4" class="fade">
    <div class="sec-head">
      <div class="sec-num">3</div>
      <div><p class="sec-eyebrow">EXAMPLE</p><h2 class="sec">코드가 겹칠 때의 저장 예시</h2></div>
    </div>
    <p class="lead">공급사 A와 B가 모두 숙소 코드 <span class="term">H-100</span>, 객실 유형 코드 <span class="term">R-001</span>을 쓰는 경우입니다. 코드가 완전히 겹치는 최악의 상황입니다.</p>

    <h3>property</h3>
    <div class="tbl-wrap">
      <table>
        <thead><tr><th>id</th><th>supplier</th><th>supplier_property_code</th><th>property_name</th></tr></thead>
        <tbody>
          <tr><td>1</td><td>A</td><td>H-100</td><td>바다호텔</td></tr>
          <tr><td>2</td><td>B</td><td>H-100</td><td>산장펜션</td></tr>
        </tbody>
      </table>
    </div>
    <p>숙소 코드가 겹쳐도 (supplier, code) UNIQUE 덕에 행이 둘로 갈리고 각각 내부 id 1·2를 받습니다. 이 id는 우리 DB가 채번한 값이라 공급사 채번 방식과 무관합니다.</p>

    <h3>room</h3>
    <div class="tbl-wrap">
      <table>
        <thead><tr><th>id</th><th>property_id</th><th>supplier_room_code</th><th>room_name</th></tr></thead>
        <tbody>
          <tr><td>10</td><td>1</td><td>R-001</td><td>디럭스</td></tr>
          <tr><td>11</td><td>2</td><td>R-001</td><td>온돌</td></tr>
        </tbody>
      </table>
    </div>
    <p>객실 유형 코드가 겹쳐도 property_id가 1과 2로 다르므로 UNIQUE에 걸리지 않습니다. 공급사가 필요하면 property를 조인해 읽습니다.</p>

    <div class="note"><span class="tag">정리</span>공급사 코드끼리 겹치는 문제는 <strong>property의 (supplier, supplier_property_code) UNIQUE</strong>에서 한 번 해결되고, room은 그 결과인 내부 id에만 매달린다. room에 supplier를 복제하면 유일성 범위는 같고 부모·자식 불일치 상태만 새로 생기므로 두지 않는다 (D-F1-9).</div>
  </section>

  <section id="s5" class="fade">
    <div class="sec-head">
      <div class="sec-num">4</div>
      <div><p class="sec-eyebrow">HISTORY</p><h2 class="sec">변경 이력</h2></div>
    </div>
    <div class="tbl-wrap">
      <table>
        <thead><tr><th>날짜</th><th>변경</th><th>근거</th></tr></thead>
        <tbody>
          <tr><td>2026-09-03</td><td>property · room_type 신설. UNIQUE 2개, FK 1개. room_type은 property_id 식별자 참조(별도 Aggregate)</td><td>F1 설계 D-F1-1~7 · PR #1</td></tr>
          <tr><td>2026-09-04</td><td>room_type UNIQUE에 supplier를 넣는 안을 검토 후 기각 — property_id가 이미 공급사를 결정</td><td>D-F1-9</td></tr>
          <tr><td>2026-09-04</td><td>room_type → room 으로 개명 (컬럼 supplier_room_code·room_name, 제약 uq_room_property_code·fk_room_property). 의미는 그대로 객실 유형</td><td>D-F1-10</td></tr>
        </tbody>
      </table>
    </div>
  </section>

  </main>

  <footer>
    <span class="gl">⁂</span>
    stay-link · DB 스키마 SSOT · 갱신 규칙은 CLAUDE.md 「기능 개발」<br>
    원본: src/main/resources/schema.sql · docs/features/property-mapping/01-design.md<br>
    다이어그램: docs/db-schema/*.json → toss-design scripts/er_schema.py 로 생성
  </footer>

</div>

<script>
  const bar=document.getElementById('bar');
  const onScroll=()=>{{const h=document.documentElement;const sc=h.scrollTop/(h.scrollHeight-h.clientHeight);bar.style.width=(sc*100)+'%';}};
  document.addEventListener('scroll',onScroll,{{passive:true}});onScroll();
  const obs=new IntersectionObserver((es)=>{{es.forEach(e=>{{if(e.isIntersecting){{e.target.classList.add('in');obs.unobserve(e.target);}}}});}},{{threshold:.08,rootMargin:'0px 0px -40px 0px'}});
  document.querySelectorAll('.fade').forEach(s=>obs.observe(s));
</script>

</body>
</html>
'''
out=f'{ROOT}/docs/db-schema.html'
open(out,'w',encoding='utf-8').write(head+body)
print('written',out)
