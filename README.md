# Development history
- ##### 시스템 전체코드는 회사의 S/W 자산이므로 개발을 담당했던 것들 중 일부 코드 요약 


- ##### Android

1. 기존 PDA용 재고관리시스템 개편(안드로이드)

	> 언어 컨버젼 (java에서 kotlin으로)   
	> 하나의 App으로 태블릿 해상도의 디바이스에도 사용가능하도록!   
	> 소스코드 : gradle 빌드 config 변경, 태블릿용 품목피킹 신규메뉴   
	> 참조URL : https://developer.android.com/studio/build?hl=ko   

- ##### Spring

1. Spring Security

	> Spring Security, jasypt 라이브러리를 사용하여 context-datasource와 사용자 패스워드를 암호화.   
	> 암호화 알고리즘 : SHA-256   
	> ※JCE(Java Cryptography Extension) install 관련하여 Exception이 발생한다면?   
	>  > Oracle 사이트에서 jdk버전에 맞는 JCE파일을 받아 설치필요 <http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html>   
	>  > local_policy.jar, US_export_policy.jar파일을 JAVA_HOME 하위 /jre/lib/security/에 덮어써준다.(=설치)   

2. Spring quartz

	> web app 내부에서 특정 업무로직을 배치처리하기 위해 사용.   
	> Spring quartz의 job 인터페이스를 구현하여 배치처리할 소스를 구현. [JobDetailBean]   
	> Linux의 crontab처럼 실행주기 트리거를 설정. [CronTriggerBean]   
	> 로직설명 : WMS 품목의 특정 플래그 변경시 SAP의 재고 플래그 변경, 미입고 구매오더/ 미출고 판매오더의 컬럼값을 순차적으로 변경해준다. [소스 코드](https://github.com/wolf2nd/portfolio/blob/master/Spring/src/main/java/wms/service/task/NaviBatchTask.java)  	

3. exampleMapper.xml

	> SQL 코딩 style 확인 위한 쿼리 2개 첨부   

- ##### Jenkins
  
1. 파이프라인 스크립트

	> Declarative 문법으로 작성한 배포 스크립트[참조 문서](https://jenkins.io/doc/book/pipeline/syntax/#declarative-pipeline)   
	> 사용 플러그인     
	>  > SQLPlus Script Runner : 빌드 전,후 웹서비스 전송플래그 관련 플래그 업데이트  [참조 문서](https://plugins.jenkins.io/sqlplus-script-runner/)   
	>  > Subversion : 반영 소스 체크아웃위한 SVN 플러그인 [참조 문서](https://plugins.jenkins.io/subversion//)   
	>  > Publish Over SSH : 빌드된 WAR파일 SSH(SFTP) 프로토콜로 전송 [참조 문서](https://plugins.jenkins.io/publish-over-ssh/)   
  
2. 부가설정

	> 사용 플러그인   
	>  > Role-based Authorization Strategy : 젠킨스유저별 메뉴 접근권한 및 역할별 실행 빌드 설정 [참조 문서](https://plugins.jenkins.io/role-strategy/)   
	>  > ThinBackup : 젠킨스 설정 및 프로젝트 아카이브 백업 [참조 문서](https://plugins.jenkins.io/thinBackup/)   
