# -*- coding: utf-8 -*-
"""给 D:\Chemdah\core\quest 下所有任务添加主职业经验奖励。
- agent.completed @ all 块追加: command papi "cttlevel addExp main {xp} %player_name%" as console
- addon.chotenui.reward 块追加: {xp} 主职业经验
- 特例: TrainTravel3 补建 agent 块; DontLaughChallenge1 移除过期"制作超级杏仁水"
- 特例: level4_lost_keys / PvpBoss 已有旧版 quetiapine level 经验, 统一换成 cttlevel
"""
import re, glob, os

BASE = r'D:\Chemdah\core\quest'

# 难度分级: 轻松 400-1000 / 标准 1000-3000 / 大型或关键 5000
XP = {
    'Tutorial': 1000,
    'TaiKuLa': 800, 'FunGesture': 800, 'HarderSelf': 1500, 'MaterialSupply': 800,
    'RollingLog': 1000, 'StoneBreakChest': 800, 'AlsoElectricity': 2500,
    'BalancedDiet': 800, 'BigStomach': 1500, 'BlackFlags': 1000, 'DarkPipe': 1000,
    'DinnerTime': 1500, 'FoodGod1': 600, 'FoodGod2': 800, 'FoodGod3': 1000,
    'FoodGod4': 1200, 'FoodGod5': 1500, 'FoodGod6': 1800, 'FoodGod7': 2500,
    'FoodGod8': 3000, 'FoodGod9': 5000, 'FoodSender': 1500, 'MedicinalHelper': 1200,
    'NianZhangShi': 2000, 'OrangeCool': 500, 'QiaoLeZi': 800,
    'AnatomyClass': 1000, 'FieldHospitalAction': 500, 'MummyPlan': 800,
    'PenicillinAction': 1000, 'RetrogradeAction': 1200, 'VaccineResearch': 1500,
    'BumperCar1': 600, 'BumperCar2': 600, 'BumperCar3': 800, 'CityWall': 1000,
    'DriftNorth': 800, 'EveryoneDontFear': 800,
    'AuthenticateHero': 1500, 'BlackHands': 1000, 'CapturedScientist': 800,
    'Containment': 1000, 'DingFuParkour': 3000, 'Groggy': 5000, 'HighClassPerson': 3000,
    'IndependentArmy': 2000, 'InfraredDetection': 5000, 'MindWindow': 1000,
    'Negotiation': 5000, 'OurPath': 1000, 'SmashPotSellIron': 2000,
    'ThresholdPath': 1500, 'YouCanDoIt': 1000,
    'TakeOffPlane': 1200, 'FakeAlmondWater1': 600, 'FakeAlmondWater2': 700,
    'KongChengJi': 1500,
    'AuxTraining': 800, 'ExcessKindness': 800, 'WantGoodPerson': 2000,
    'WarehouseSearch': 600,
    'WorkForFuture1': 800, 'WorkForFuture2': 800, 'WorkForFuture3': 800,
    'WorkForFuture4': 1500,
    'AfterMidnight': 1500, 'CuttingRoomFloor': 1200, 'DontSurf': 1500,
    'LoveLi2CO3': 600, 'OldRoomBlues': 600, 'ToolboxTheory': 2000,
    'InternetTravel1': 800, 'InternetTravel2': 2000, 'InternetTravel3': 800,
    'TrainTravel1': 1500, 'TrainTravel2': 1000, 'TrainTravel3': 1500,
    'XiangLiCar': 1000, 'Capable': 2000, 'SufferingChallenge': 1800,
    'SufferingGoal': 1500, 'BBQDone': 800, 'EaseUp': 800, 'OldIceBBQ': 1000,
    'CantSleep': 800, 'ClawMachinePower': 600, 'SorrowWater': 800,
    'QingQingShi': 5000, 'DontLaughChallenge1': 700, 'DontLaughChallenge2': 600,
    'DontLaughChallenge3': 1500, 'MysteriousObservatory': 800, 'RawCola': 800,
    'SeabuckthornJuice': 700, 'Sponsorships1': 800,
    'JiFangWeiHu': 800, 'ManBaOut': 700, 'PvpBoss': 2000, 'ShenJin': 800, 'ZuLiJian': 700,
    'RunningToTheNight': 1500, 'SatTeeTouy': 600, 'CourageToInnovate': 1000,
    'OnTheJob': 800, 'PursuingPerformance': 1200, 'UnityCooperation': 800,
    'Panopticoal1': 600, 'Panopticoal2': 800, 'Panopticoal3': 1500, 'Panopticoal4': 1500,
    'BellyHurts': 800, 'FlashGoldCharge': 2500, 'LastShift': 800, 'OhYeah': 600,
    'Rocket': 5000,
    'CleanUpAction': 1500, 'GatewayLeading': 1000, 'NewTypeOfMedication': 1000,
    'SearchForQuinn': 800, 'CherryBlossom': 2000, 'PotatoLight': 1000,
    'SearchForGreybird': 600, 'GatewayLeading_Raven': 700, 'SecretGoods': 1200,
    'Chicken_is_just_chicken': 1500, 'FishermanAndCat': 800, 'InspirationMushroom': 800,
    'PetCollar': 600, 'GatewayLeading_Vulcan': 700,
    'ResourceAssurance_Level11': 600, 'ResourceAssurance_Level11_2': 600,
    'CheckTheExit_Level5': 800, 'CleanUpAction_Level5': 1000, 'ResourceAssurance_Level5': 600,
    'level4_lost_keys': 500, 'BeerHallFriedRice': 600, 'BetaGame': 800,
    'ShenyangStreet': 1500, 'YiYanWanNian': 800,
    'FacingDespair': 5000, 'LonelyWalk': 1200, 'TuiTuiTui': 1200,
    'DistantFriend': 1500, 'SurvivalOfTheFittest': 1200, 'TheaterEffect': 2000,
    'TheFirstRow': 3000,
    'AdultChoice': 1500, 'WantAll': 1200, 'CannedLaughter': 5000,
    'AppleAndBanana': 600,
    'IgniteFirewood': 5000, 'TheNewOrderAlpha': 5000,
    'CustomsTour': 1000, 'DutyLeave': 2000, 'Paronychia': 800,
    'LittleEasyAdventure': 5000, 'MachineCustomerService': 800, 'NormalAdventure': 1000,
    'OtakuAdventure': 800, 'QingMingJiu': 800, 'SoJu': 500, 'StrangeAdventure': 800,
    'GoldenGambol': 2500, 'Komi': 3000, 'MongoliaShangDan': 800,
    'ParkourCivilization': 1200, 'RookieRoad': 800, 'ScrapMetal': 1500, 'YouthDance': 800,
    'UncommonAbility1': 1000, 'UncommonAbility2': 1500, 'UncommonAbility3': 2000,
    'UncommonAbility4': 5000,
}

SKIP = {'Test1'}  # 测试任务
STRIP_SUPER_ALMOND = {'TrainTravel3', 'DontLaughChallenge1'}  # object_100 已移除
LEGACY_XP = {'level4_lost_keys': 500, 'PvpBoss': 2000}  # 已有旧 quetiapine 经验

CMD_FMT = 'command papi "cttlevel addExp main {xp} %player_name%" as console'
RWD_FMT = '{xp} 主职业经验'


def indent_of(line):
    return len(line) - len(line.lstrip(' '))


def find_top_keys(lines):
    keys = []
    for i, ln in enumerate(lines):
        if ln.startswith(' ') or ln.strip() == '':
            continue
        if re.match(r'^[A-Za-z_][A-Za-z0-9_]*:', ln):
            keys.append((i, re.match(r'^([A-Za-z_][A-Za-z0-9_]*)', ln).group(1)))
    return keys


def find_key_line(lines, start, end, key):
    for i in range(start, end):
        if lines[i].strip().startswith(key + ':'):
            return i
    return None


def find_quest_agent(lines, start, end):
    """任务级 agent: 缩进为 2 的行(任务根的子键)。"""
    for i in range(start, end):
        if lines[i].strip() == 'agent:' and indent_of(lines[i]) == 2:
            return i
    return None


def find_completed_under(lines, agent_i, end):
    if agent_i is None:
        return None
    for i in range(agent_i + 1, end):
        if lines[i].strip().startswith('completed @ all') and indent_of(lines[i]) == 4:
            return i
    return None


def literal_block_end(lines, key_idx):
    ki = indent_of(lines[key_idx])
    j = key_idx + 1
    while j < len(lines):
        ln = lines[j]
        if ln.strip() == '':
            j += 1
            continue
        if indent_of(ln) <= ki:
            break
        j += 1
    return j


def crlf_of(raw):
    return '\r\n' in raw


def make_line(text, crlf):
    return text + ('\r' if crlf else '')


report = []
for f in sorted(glob.glob(os.path.join(BASE, '**', '*.yml'), recursive=True)):
    raw = open(f, encoding='utf-8').read()
    crlf = crlf_of(raw)
    lines = raw.split('\n')
    if lines and lines[-1] == '':
        lines.pop()
    tops = find_top_keys(lines)
    modified = False
    for idx, (ti, qid) in enumerate(tops):
        if qid in SKIP:
            report.append(f"{os.path.relpath(f, BASE)} | {qid} | 跳过(测试/未完成)")
            continue
        if qid not in XP:
            report.append(f"{os.path.relpath(f, BASE)} | {qid} | !!! 无XP映射")
            continue
        xp = XP[qid]
        te = tops[idx + 1][0] if idx + 1 < len(tops) else len(lines)

        # --- 1) completed @ all 命令(任务级 agent 块) ---
        agent_i = find_quest_agent(lines, ti, te)
        ci = find_completed_under(lines, agent_i, te)
        if qid in LEGACY_XP:
            # 替换旧 quetiapine 命令(保留原缩进)
            if ci is not None:
                for i in range(ci, literal_block_end(lines, ci)):
                    if 'quetiapine level addExp' in lines[i]:
                        lines[i] = ' ' * indent_of(lines[i]) + make_line(CMD_FMT.format(xp=xp), crlf)
                        modified = True
                        break
            else:
                report.append(f"{os.path.relpath(f, BASE)} | {qid} | !!! LEGACY 但无 completed 块")
        elif ci is not None:
            ins = literal_block_end(lines, ci)
            lines.insert(ins, make_line(' ' * (indent_of(lines[ci]) + 2) + CMD_FMT.format(xp=xp), crlf))
            modified = True
        else:
            # 无任务级 completed 块 -> 在任务根下补建 agent 块
            insert_at = te
            while insert_at > ti and lines[insert_at - 1].strip() == '':
                insert_at -= 1
            block = [
                make_line('  agent:', crlf),
                make_line('    completed @ all: |-', crlf),
                make_line('      ' + CMD_FMT.format(xp=xp), crlf),
            ]
            for j, ln in enumerate(block):
                lines.insert(insert_at + j, ln)
            modified = True

        # --- 2) reward 描述 ---
        ri = find_key_line(lines, ti, te, 'reward')
        if ri is None:
            report.append(f"{os.path.relpath(f, BASE)} | {qid} | !!! 无 reward 块")
            continue
        rend = literal_block_end(lines, ri)
        ci2 = indent_of(lines[ri]) + 2
        if qid in LEGACY_XP:
            # 替换 "100 经验值"
            done = False
            for i in range(ri + 1, rend):
                if '经验值' in lines[i]:
                    lines[i] = make_line(' ' * ci2 + RWD_FMT.format(xp=xp), crlf)
                    modified = True
                    done = True
                    break
            if not done:
                report.append(f"{os.path.relpath(f, BASE)} | {qid} | !!! LEGACY 但 reward 无经验值行")
        else:
            content = []
            for i in range(ri + 1, rend):
                ln = lines[i]
                if qid in STRIP_SUPER_ALMOND and '制作超级杏仁水' in ln:
                    modified = True
                    continue
                content.append(ln)
            content.append(make_line(' ' * ci2 + RWD_FMT.format(xp=xp), crlf))
            if content != lines[ri + 1:rend] or (lines[ri + 1:rend] != [] and len(content) != rend - ri - 1):
                lines[ri + 1:rend] = content
                modified = True

        report.append(f"{os.path.relpath(f, BASE)} | {qid} | +{xp} XP")

    if modified:
        out = '\n'.join(lines) + ('\r\n' if crlf else '\n')
        with open(f, 'w', encoding='utf-8', newline='') as fh:
            fh.write(out)

for r in report:
    print(r)
print(f"\n共处理 {len(report)} 条")
