$ErrorActionPreference = 'Stop'

$schemaUrl = 'https://raw.githubusercontent.com/dineug/erd-editor/main/json-schema/schema.json'
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

$tables = [ordered]@{
    users = @{
        position = @(80, 540)
        color = '#4C78A8'
        comment = '사용자 계정, 전적, 모드별 레이팅 및 AI 진행도'
        columns = @(
            @('id', 'BIGINT', '', 11, '내부 PK'),
            @('public_id', 'BINARY(16)', '', 12, '외부 노출 UUID; UNIQUE'),
            @('email', 'VARCHAR(100)', '', 12, 'UNIQUE'),
            @('password', 'VARCHAR(200)', '', 0, '소셜/BOT 계정은 NULL'),
            @('nickname', 'VARCHAR(30)', '', 12, 'UNIQUE'),
            @('role', 'VARCHAR(20)', "'USER'", 8, ''),
            @('provider', 'VARCHAR(20)', "'LOCAL'", 8, ''),
            @('provider_id', 'VARCHAR(200)', '', 0, ''),
            @('email_verified', 'BOOLEAN', 'FALSE', 8, ''),
            @('profile_private', 'BOOLEAN', 'FALSE', 8, ''),
            @('profile_image_url', 'VARCHAR(500)', '', 0, ''),
            @('wins', 'INT', '0', 8, ''),
            @('losses', 'INT', '0', 8, ''),
            @('draws', 'INT', '0', 8, ''),
            @('currency', 'INT', '0', 8, ''),
            @('token_version', 'BIGINT', '0', 8, ''),
            @('classic_rating', 'INT', '1000', 8, ''),
            @('physical_rating', 'INT', '1000', 8, ''),
            @('ai_cleared_level', 'INT', '0', 8, ''),
            @('created_at', 'DATETIME(6)', '', 8, ''),
            @('updated_at', 'DATETIME(6)', '', 8, '')
        )
    }
    rooms = @{
        position = @(620, 60)
        color = '#F58518'
        comment = '대국방 설정과 현재 상태'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('room_code', 'VARCHAR(10)', '', 12, 'UNIQUE'),
            @('host_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('status', 'VARCHAR(20)', "'WAITING'", 8, ''),
            @('game_type', 'VARCHAR(20)', "'CLASSIC'", 8, ''),
            @('time_limit', 'VARCHAR(20)', "'UNLIMITED'", 8, ''),
            @('byoyomi_option', 'VARCHAR(20)', "'NONE'", 8, ''),
            @('max_spectators', 'INT', '3', 8, ''),
            @('current_game_number', 'INT', '0', 8, ''),
            @('omok_rule', 'VARCHAR(20)', "'FREESTYLE'", 8, ''),
            @('ranked', 'BOOLEAN', 'TRUE', 8, ''),
            @('created_at', 'DATETIME(6)', '', 8, '')
        )
    }
    games = @{
        position = @(1160, 60)
        color = '#E45756'
        comment = '방에서 진행된 개별 대국'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('room_id', 'BIGINT', '', 8, 'FK → rooms.id'),
            @('game_number', 'INT', '1', 8, ''),
            @('black_player_id', 'BIGINT', '', 0, 'FK → users.id; ON DELETE SET NULL'),
            @('white_player_id', 'BIGINT', '', 0, 'FK → users.id; ON DELETE SET NULL'),
            @('winner_id', 'BIGINT', '', 0, 'FK → users.id; ON DELETE SET NULL'),
            @('status', 'VARCHAR(20)', "'WAITING'", 8, ''),
            @('current_turn', 'VARCHAR(10)', '', 0, ''),
            @('created_at', 'DATETIME(6)', '', 8, ''),
            @('started_at', 'DATETIME(6)', '', 0, ''),
            @('finished_at', 'DATETIME(6)', '', 0, ''),
            @('last_move_at', 'DATETIME(6)', '', 0, '')
        )
    }
    game_players = @{
        position = @(620, 620)
        color = '#72B7B2'
        comment = '방 참여자; UNIQUE(room_id, user_id)'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('room_id', 'BIGINT', '', 8, 'FK → rooms.id'),
            @('user_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('role', 'VARCHAR(20)', '', 8, ''),
            @('color', 'VARCHAR(10)', '', 0, ''),
            @('remaining_seconds', 'INT', '', 0, ''),
            @('in_byoyomi', 'BOOLEAN', 'FALSE', 8, ''),
            @('is_ready', 'BOOLEAN', 'FALSE', 8, ''),
            @('joined_at', 'DATETIME(6)', '', 8, '')
        )
    }
    game_moves = @{
        position = @(1700, 60)
        color = '#54A24B'
        comment = '일반 대국의 착수 기록'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('game_id', 'BIGINT', '', 8, 'FK → games.id'),
            @('player_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('color', 'VARCHAR(10)', '', 8, ''),
            @('row_pos', 'INT', '', 8, ''),
            @('col', 'INT', '', 8, ''),
            @('move_number', 'INT', '', 8, ''),
            @('created_at', 'DATETIME(6)', '', 8, '')
        )
    }
    physical_game_records = @{
        position = @(1700, 500)
        color = '#B279A2'
        comment = '물리/특수 대국 JSON 리플레이; UNIQUE(game_id)'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('game_id', 'BIGINT', '', 12, 'FK → games.id; UNIQUE'),
            @('replay', 'JSON', '', 8, ''),
            @('created_at', 'DATETIME(6)', 'CURRENT_TIMESTAMP(6)', 8, '')
        )
    }
    items = @{
        position = @(620, 1160)
        color = '#FF9DA6'
        comment = '상점/커스터마이징 아이템 카탈로그'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('name', 'VARCHAR(100)', '', 8, ''),
            @('item_type', 'VARCHAR(50)', '', 8, ''),
            @('description', 'VARCHAR(500)', '', 0, ''),
            @('item_config', 'JSON', '', 0, ''),
            @('default_grant', 'BOOLEAN', 'FALSE', 8, ''),
            @('created_at', 'DATETIME(6)', 'CURRENT_TIMESTAMP(6)', 8, '')
        )
    }
    user_items = @{
        position = @(80, 1320)
        color = '#9D755D'
        comment = '사용자 보유 아이템; UNIQUE(user_id, item_id)'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('user_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('item_id', 'BIGINT', '', 8, 'FK → items.id'),
            @('acquired_at', 'DATETIME(6)', 'CURRENT_TIMESTAMP(6)', 8, '')
        )
    }
    user_active_items = @{
        position = @(1160, 1160)
        color = '#BAB0AC'
        comment = '유형별 장착 아이템; UNIQUE(user_id, item_type)'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('user_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('item_type', 'VARCHAR(50)', '', 8, ''),
            @('item_id', 'BIGINT', '', 8, 'FK → items.id')
        )
    }
    friendships = @{
        position = @(80, 60)
        color = '#8F63B8'
        comment = '방향성 친구 요청; UNIQUE(requester_id, addressee_id)'
        columns = @(
            @('id', 'BIGINT', '', 11, ''),
            @('requester_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('addressee_id', 'BIGINT', '', 8, 'FK → users.id'),
            @('status', 'VARCHAR(20)', '', 8, ''),
            @('created_at', 'DATETIME(6)', '', 8, ''),
            @('updated_at', 'DATETIME(6)', '', 8, '')
        )
    }
}

$foreignKeys = @(
    @('rooms', 'host_id', 'users', 'id', $false),
    @('games', 'room_id', 'rooms', 'id', $false),
    @('games', 'black_player_id', 'users', 'id', $true),
    @('games', 'white_player_id', 'users', 'id', $true),
    @('games', 'winner_id', 'users', 'id', $true),
    @('game_players', 'room_id', 'rooms', 'id', $false),
    @('game_players', 'user_id', 'users', 'id', $false),
    @('game_moves', 'game_id', 'games', 'id', $false),
    @('game_moves', 'player_id', 'users', 'id', $false),
    @('physical_game_records', 'game_id', 'games', 'id', $false),
    @('user_items', 'user_id', 'users', 'id', $false),
    @('user_items', 'item_id', 'items', 'id', $false),
    @('user_active_items', 'user_id', 'users', 'id', $false),
    @('user_active_items', 'item_id', 'items', 'id', $false),
    @('friendships', 'requester_id', 'users', 'id', $false),
    @('friendships', 'addressee_id', 'users', 'id', $false)
)

function New-Meta {
    return [ordered]@{ updateAt = $now; createAt = $now }
}

$tableEntities = [ordered]@{}
$columnEntities = [ordered]@{}
$columnIdByName = @{}
$tableIds = @()

foreach ($tableName in $tables.Keys) {
    $tableId = "table-$tableName"
    $tableIds += $tableId
    $columnIds = @()

    foreach ($column in $tables[$tableName].columns) {
        $columnName = $column[0]
        $columnId = "column-$tableName-$columnName"
        $columnIds += $columnId
        $columnIdByName["$tableName.$columnName"] = $columnId
        $keys = if (($column[3] -band 2) -ne 0) { 1 } else { 0 }

        $columnEntities[$columnId] = [ordered]@{
            id = $columnId
            tableId = $tableId
            name = $columnName
            comment = $column[4]
            dataType = $column[1]
            default = $column[2]
            options = $column[3]
            ui = [ordered]@{
                keys = $keys
                widthName = 140
                widthComment = 220
                widthDataType = 120
                widthDefault = 130
            }
            meta = New-Meta
        }
    }

    $tableEntities[$tableId] = [ordered]@{
        id = $tableId
        name = $tableName
        comment = $tables[$tableName].comment
        columnIds = $columnIds
        seqColumnIds = $columnIds
        ui = [ordered]@{
            x = $tables[$tableName].position[0]
            y = $tables[$tableName].position[1]
            zIndex = 1
            widthName = 180
            widthComment = 320
            color = $tables[$tableName].color
        }
        meta = New-Meta
    }
}

$relationshipEntities = [ordered]@{}
$relationshipIds = @()
$relationshipNumber = 0

foreach ($fk in $foreignKeys) {
    $relationshipNumber++
    $childTable = $fk[0]
    $childColumn = $fk[1]
    $parentTable = $fk[2]
    $parentColumn = $fk[3]
    $nullable = $fk[4]
    $relationshipId = "relationship-$relationshipNumber-$childTable-$childColumn"
    $relationshipIds += $relationshipId
    $columnEntities[$columnIdByName["$childTable.$childColumn"]].ui.keys = 2

    $relationshipEntities[$relationshipId] = [ordered]@{
        id = $relationshipId
        identification = $false
        relationshipType = 16
        startRelationshipType = $(if ($nullable) { 1 } else { 2 })
        start = [ordered]@{
            tableId = "table-$parentTable"
            columnIds = @($columnIdByName["$parentTable.$parentColumn"])
            x = 0
            y = 0
            direction = 2
        }
        end = [ordered]@{
            tableId = "table-$childTable"
            columnIds = @($columnIdByName["$childTable.$childColumn"])
            x = 0
            y = 0
            direction = 1
        }
        meta = New-Meta
    }
}

$erd = [ordered]@{
    '$schema' = $schemaUrl
    version = '3.0.0'
    settings = [ordered]@{
        width = 2400
        height = 2000
        scrollTop = 0
        scrollLeft = 0
        zoomLevel = 0.65
        show = 511
        database = 4
        databaseName = 'dopamin_omok'
        canvasType = 'ERD'
        language = 8
        tableNameCase = 8
        columnNameCase = 8
        bracketType = 8
        relationshipDataTypeSync = $true
        relationshipOptimization = $true
        columnOrder = @(1, 2, 4, 8, 16, 32, 64)
        maxWidthComment = 320
        ignoreSaveSettings = 0
    }
    doc = [ordered]@{
        tableIds = $tableIds
        relationshipIds = $relationshipIds
        indexIds = @()
        memoIds = @()
    }
    collections = [ordered]@{
        tableEntities = $tableEntities
        tableColumnEntities = $columnEntities
        relationshipEntities = $relationshipEntities
        indexEntities = [ordered]@{}
        indexColumnEntities = [ordered]@{}
        memoEntities = [ordered]@{}
    }
}

$target = Join-Path $PSScriptRoot '..\database.erd'
$erd | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $target -Encoding utf8
Write-Output "Generated $target"
