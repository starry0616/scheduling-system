# ============================================================
# 任务7 真实小规模排课运行编排脚本
# 前置: 后端应用已在 http://localhost:8080 启动(spring-boot:run)
# 用法: powershell -ExecutionPolicy Bypass -File task7-real-run.ps1
# ============================================================
$ErrorActionPreference = 'Stop'
$env:MYSQL_PWD = '123456'
$base = 'http://localhost:8080/api'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$suffix = Get-Date -Format 'HHmmssfff'
$hash = '$2a$10$N.ZOn9G6/YLFixkdFvX0D.S6vEDQvXaFY1gkpr1cK8sF3lZl9JwJe'

Write-Host "== 步骤0: 登录 admin =="
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body '{"username":"admin","password":"admin123"}'
if ($login.code -ne 200) { throw '登录失败' }
$token = $login.data.token
$H = @{ Authorization = "Bearer $token" }
Write-Host "登录成功, token 长度=$($token.Length), 批次后缀=$suffix"

function PostJson($url, $body) {
    $r = Invoke-RestMethod -Method Post -Uri "$base$url" -Headers $H -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 6)
    if ($r.code -ne 200) { throw "$url 失败: $($r | ConvertTo-Json -Depth 6)" }
    return $r.data
}
function PutJson($url, $body) {
    $r = Invoke-RestMethod -Method Put -Uri "$base$url" -Headers $H -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 6)
    if ($r.code -ne 200) { throw "$url 失败: $($r | ConvertTo-Json -Depth 6)" }
    return $r.data
}
function Q($sql) {
    # 沙箱内必须使用长选项形式(短选项会被截断); --skip-column-names 只取数据行
    $out = & $mysql --host=127.0.0.1 --user=root --skip-column-names --default-character-set=utf8mb4 --batch --raw scheduling_system "--execute=$sql" 2>$null
    if ($LASTEXITCODE -ne 0) { throw "mysql 执行失败 exit=$LASTEXITCODE sql=$sql" }
    return $out
}
function GetId($sql, $desc) {
    $rows = @(Q $sql)
    if ($rows.Count -lt 1 -or [string]::IsNullOrWhiteSpace($rows[0])) { throw "未取到 $desc : $sql" }
    return ($rows[0].Trim())
}

# ---------- 1. 直接落库: 两名教师的登录账号(sys_user) ----------
$userSql = @"
INSERT INTO sys_user (username, password, real_name, role, status) VALUES
('real7_t1_$suffix', '$hash', '张伟', 'TEACHER', 1),
('real7_t2_$suffix', '$hash', '李静', 'TEACHER', 1);
"@
Q $userSql | Out-Null
$uid1 = GetId "SELECT id FROM sys_user WHERE username='real7_t1_$suffix'" '用户t1'
$uid2 = GetId "SELECT id FROM sys_user WHERE username='real7_t2_$suffix'" '用户t2'
Write-Host "已创建用户 real7_t1/t2, uid=$uid1/$uid2"

# ---------- 2. HTTP API: 教师/班级/课程/教室 ----------
PostJson '/teachers' @{ userId = [long]$uid1; teacherNo = "R7T1$suffix"; name = '张伟'; title = '副教授'; department = '计算机学院' } | Out-Null
PostJson '/teachers' @{ userId = [long]$uid2; teacherNo = "R7T2$suffix"; name = '李静'; title = '讲师'; department = '计算机学院' } | Out-Null
$tid1 = GetId "SELECT id FROM teacher WHERE teacher_no='R7T1$suffix'" '教师t1'
$tid2 = GetId "SELECT id FROM teacher WHERE teacher_no='R7T2$suffix'" '教师t2'

PostJson '/classes' @{ className = "计科2401-$suffix"; grade = '2024级'; studentCount = 45; department = '计算机学院' } | Out-Null
PostJson '/classes' @{ className = "计科2402-$suffix"; grade = '2024级'; studentCount = 45; department = '计算机学院' } | Out-Null
$cid1 = GetId "SELECT id FROM class WHERE class_name LIKE '%2401-$suffix'" '班级A'
$cid2 = GetId "SELECT id FROM class WHERE class_name LIKE '%2402-$suffix'" '班级B'

PostJson '/courses' @{ courseCode = "DS$suffix"; courseName = "数据结构-$suffix"; courseType = 'THEORY'; requiredRoomType = 'NORMAL' } | Out-Null
PostJson '/courses' @{ courseCode = "PHY$suffix"; courseName = "大学物理-$suffix"; courseType = 'THEORY'; requiredRoomType = 'MULTIMEDIA' } | Out-Null
PostJson '/courses' @{ courseCode = "SE-LAB$suffix"; courseName = "软件工程实验-$suffix"; courseType = 'LAB'; requiredRoomType = 'LAB' } | Out-Null
$courseDS = GetId "SELECT id FROM course WHERE course_code='DS$suffix'" '课程DS'
$coursePHY = GetId "SELECT id FROM course WHERE course_code='PHY$suffix'" '课程PHY'
$courseLAB = GetId "SELECT id FROM course WHERE course_code='SE-LAB$suffix'" '课程LAB'

PostJson '/classrooms' @{ roomNo = "R7N$suffix"; building = '第一教学楼'; capacity = 120; roomType = 'NORMAL' } | Out-Null
PostJson '/classrooms' @{ roomNo = "R7M$suffix"; building = '第二教学楼'; capacity = 120; roomType = 'MULTIMEDIA' } | Out-Null
PostJson '/classrooms' @{ roomNo = "R7L$suffix"; building = '实验楼'; capacity = 60; roomType = 'LAB' } | Out-Null
$roomN = GetId "SELECT id FROM classroom WHERE room_no='R7N$suffix'" '教室N'
$roomM = GetId "SELECT id FROM classroom WHERE room_no='R7M$suffix'" '教室M'
$roomL = GetId "SELECT id FROM classroom WHERE room_no='R7L$suffix'" '教室L'
Write-Host "资源就绪: 教师[$tid1,$tid2] 班级[$cid1,$cid2] 课程[$courseDS,$coursePHY,$courseLAB] 教室[$roomN,$roomM,$roomL]"

# ---------- 3. HTTP API: 开课实例 + 班级关联 ----------
# o1 数据结构(理论/普通) 张伟 x 计科2401 每周2次每次1节
# o2 数据结构(理论/普通) 张伟 x 计科2402 每周2次每次1节
# o3 大学物理(理论/多媒体) 李静 x 计科2401 每周2次每次1节
# o4 软件工程实验(实验/LAB) 李静 x 计科2402 每周2次每次连续2节
PostJson '/course-offerings' @{ courseId = [long]$courseDS; teacherId = [long]$tid1; semester = '2026秋'; weeklySessions = 2; durationSlots = 1 } | Out-Null
PostJson '/course-offerings' @{ courseId = [long]$courseDS; teacherId = [long]$tid1; semester = '2026秋'; weeklySessions = 2; durationSlots = 1 } | Out-Null
PostJson '/course-offerings' @{ courseId = [long]$coursePHY; teacherId = [long]$tid2; semester = '2026秋'; weeklySessions = 2; durationSlots = 1 } | Out-Null
PostJson '/course-offerings' @{ courseId = [long]$courseLAB; teacherId = [long]$tid2; semester = '2026秋'; weeklySessions = 2; durationSlots = 2 } | Out-Null
$o1 = GetId "SELECT id FROM course_offering WHERE teacher_id=$tid1 AND course_id=$courseDS ORDER BY id LIMIT 1" '开课o1'
$o2 = GetId "SELECT id FROM course_offering WHERE teacher_id=$tid1 AND course_id=$courseDS ORDER BY id DESC LIMIT 1" '开课o2'
$o3 = GetId "SELECT id FROM course_offering WHERE teacher_id=$tid2 AND course_id=$coursePHY LIMIT 1" '开课o3'
$o4 = GetId "SELECT id FROM course_offering WHERE teacher_id=$tid2 AND course_id=$courseLAB LIMIT 1" '开课o4'

PutJson "/course-offerings/$o1/classes" @{ classIds = @([long]$cid1) } | Out-Null
PutJson "/course-offerings/$o2/classes" @{ classIds = @([long]$cid2) } | Out-Null
PutJson "/course-offerings/$o3/classes" @{ classIds = @([long]$cid1) } | Out-Null
PutJson "/course-offerings/$o4/classes" @{ classIds = @([long]$cid2) } | Out-Null
Write-Host "开课就绪: o1=$o1 o2=$o2 o3=$o3 o4=$o4 (每周合计 8 次课)"

# ---------- 4. HTTP API: 任务 + 范围 + 执行 ----------
$task = PostJson '/scheduling-tasks' @{ taskName = "Task7-Real-Run-$suffix"; semester = '2026秋'; randomSeed = 20260905 }
$taskId = $task.id
if (-not $taskId) { $taskId = GetId "SELECT id FROM scheduling_task WHERE task_name='Task7-Real-Run-$suffix'" '任务id' }
PutJson "/scheduling-tasks/$taskId/course-offerings" @{ courseOfferingIds = @([long]$o1, [long]$o2, [long]$o3, [long]$o4) } | Out-Null
PutJson "/scheduling-tasks/$taskId/classrooms" @{ classroomIds = @([long]$roomN, [long]$roomM, [long]$roomL) } | Out-Null
Write-Host "任务已创建并配置范围: taskId=$taskId"
Set-Content -Path "c:\Users\shayin\WorkBuddy AI\2026-09-05-11-57-48\scheduling-system\scripts\logs\last-task7.txt" -Value $taskId -Encoding utf8

$runResp = Invoke-RestMethod -Method Post -Uri "$base/scheduling-tasks/$taskId/run" -Headers $H
if ($runResp.code -ne 200) { throw "run 失败: $($runResp | ConvertTo-Json -Depth 8)" }
Write-Host "== run 响应 =="
$runResp.data | ConvertTo-Json -Depth 8

# ---------- 5. 数据库验证(逐节展开 duration 审计三维冲突) ----------
$entryRows = @(Q "SELECT e.id, co.duration_slots, e.teacher_id, e.classroom_id, ts.day_of_week, ts.period FROM schedule_entry e JOIN course_offering co ON co.id=e.course_offering_id JOIN time_slot ts ON ts.id=e.time_slot_id WHERE e.task_id=$taskId ORDER BY e.id")
$classRows = @(Q "SELECT ec.schedule_entry_id, ec.class_id FROM schedule_entry_class ec JOIN schedule_entry e ON e.id=ec.schedule_entry_id WHERE e.task_id=$taskId")
$occupied = @{}
$conflicts = @()
$entryCount = 0
foreach ($line in $entryRows) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $f = $line -split "`t"
    $eid = [long]$f[0]; $dur = [int]$f[1]; $tid = [long]$f[2]; $rid = [long]$f[3]
    $day = [int]$f[4]; $per = [int]$f[5]
    $klasses = @($classRows | Where-Object { $_ -match "^$eid`t" } | ForEach-Object { ($_ -split "`t")[1] })
    $entryCount++
    for ($k = 0; $k -lt $dur; $k++) {
        $p = $per + $k
        foreach ($res in @("T$tid", "R$rid")) {
            $key = "$res@$day#$p"
            if ($occupied.ContainsKey($key)) { $conflicts += "资源 $res 在 周$day 第${p}节 冲突: entry$($occupied[$key]) vs entry$eid" }
            else { $occupied[$key] = $eid }
        }
        foreach ($cid in $klasses) {
            $key = "K$cid@$day#$p"
            if ($occupied.ContainsKey($key)) { $conflicts += "班级$cid 在 周$day 第${p}节 冲突: entry$($occupied[$key]) vs entry$eid" }
            else { $occupied[$key] = $eid }
        }
    }
}
Write-Host "schedule_entry 行数 = $entryCount (期望 8)"
if ($conflicts.Count -eq 0) { Write-Host 'PASS: 教师/班级/教室三维资源零时间冲突(含连续2节逐节展开)' }
else { Write-Host ('FAIL 冲突数=' + $conflicts.Count); $conflicts }

# 每开课实例条目数与 weekly_sessions 对齐
$sess = @(Q "SELECT co.id, co.weekly_sessions, COUNT(e.id) FROM course_offering co LEFT JOIN schedule_entry e ON e.course_offering_id=co.id AND e.task_id=$taskId WHERE co.id IN ($o1,$o2,$o3,$o4) GROUP BY co.id, co.weekly_sessions ORDER BY co.id")
foreach ($s in $sess) { Write-Host "offering $s" }

Write-Host "全部完成, taskId=$taskId"