package.preload['temper-regex-engine/ctype'] = function()
  local temper = require('temper-core');
  local wordCharsCalc, spaceCharsCalc, digitCharsCalc, bitsToList, wordChars, spaceChars, wordCharsList, spaceCharsList, digitCharsList, isWordChar, exports;
  wordCharsCalc = function()
    local bitset__16, i__17, i__18, i__19;
    bitset__16 = temper.densebitvector_constructor(0);
    i__17 = 48;
    while (i__17 <= 57) do
      temper.densebitvector_set(bitset__16, i__17, true);
      i__17 = temper.int32_add(i__17, 1);
    end
    i__18 = 65;
    while (i__18 <= 90) do
      temper.densebitvector_set(bitset__16, i__18, true);
      i__18 = temper.int32_add(i__18, 1);
    end
    i__19 = 97;
    while (i__19 <= 122) do
      temper.densebitvector_set(bitset__16, i__19, true);
      i__19 = temper.int32_add(i__19, 1);
    end
    temper.densebitvector_set(bitset__16, 95, true);
    return bitset__16;
  end;
  spaceCharsCalc = function()
    local bitset__21;
    bitset__21 = temper.densebitvector_constructor(0);
    temper.densebitvector_set(bitset__21, 32, true);
    temper.densebitvector_set(bitset__21, 13, true);
    temper.densebitvector_set(bitset__21, 10, true);
    temper.densebitvector_set(bitset__21, 9, true);
    temper.densebitvector_set(bitset__21, 11, true);
    temper.densebitvector_set(bitset__21, 12, true);
    return bitset__21;
  end;
  digitCharsCalc = function()
    local bitset__23, i__24;
    bitset__23 = temper.densebitvector_constructor(0);
    i__24 = 48;
    while (i__24 <= 57) do
      temper.densebitvector_set(bitset__23, i__24, true);
      i__24 = temper.int32_add(i__24, 1);
    end
    return bitset__23;
  end;
  bitsToList = function(bitset__25, max__26)
    local ls__28, i__29;
    ls__28 = temper.listbuilder_constructor();
    i__29 = 0;
    while (i__29 < max__26) do
      if temper.densebitvector_get(bitset__25, i__29) then
        temper.listbuilder_add(ls__28, i__29);
      end
      i__29 = temper.int32_add(i__29, 1);
    end
    return ls__28;
  end;
  wordChars = wordCharsCalc();
  spaceChars = spaceCharsCalc();
  wordCharsList = bitsToList(wordChars, 128);
  spaceCharsList = bitsToList(spaceChars, 128);
  digitCharsList = bitsToList(digitCharsCalc(), 128);
  isWordChar = function(c__30)
    if temper.densebitvector_get(wordChars, c__30) then
      return true;
    else
      return (c__30 >= 256);
    end
  end;
  exports = {};
  exports.wordChars = wordChars;
  exports.spaceChars = spaceChars;
  exports.wordCharsList = wordCharsList;
  exports.spaceCharsList = spaceCharsList;
  exports.digitCharsList = digitCharsList;
  exports.isWordChar = isWordChar;
  return exports;
end
package.preload['temper-regex-engine/dfa/dfa'] = function()
  local temper = require('temper-core');
  local charCodeMatcher, fromChars, regexToString, bogusMatch, chr, SingleMatcher, ContainsMatcher, BeginMatcher, WordBoundaryMatcher, EndMatcher, StartGroupMatcher, EpsilonMatcher, EndGroupMatcher, Target, Row, Table, Alphabet, DFA, noTarget, noTable, isEmptyEdge, epsilonClosure, exports;
  charCodeMatcher = temper.import('temper-regex-engine/nfa/matcher', 'charCodeMatcher');
  fromChars = temper.import('temper-regex-engine/nfa/matcher', 'fromChars');
  regexToString = temper.import('temper-regex-engine/util', 'regexToString');
  bogusMatch = temper.import('temper-regex-engine/util', 'bogusMatch');
  chr = temper.import('temper-regex-engine/util', 'chr');
  SingleMatcher = temper.import('temper-regex-engine/nfa/matcher', 'SingleMatcher');
  ContainsMatcher = temper.import('temper-regex-engine/nfa/matcher', 'ContainsMatcher');
  BeginMatcher = temper.import('temper-regex-engine/nfa/matcher', 'BeginMatcher');
  WordBoundaryMatcher = temper.import('temper-regex-engine/nfa/matcher', 'WordBoundaryMatcher');
  EndMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EndMatcher');
  StartGroupMatcher = temper.import('temper-regex-engine/nfa/matcher', 'StartGroupMatcher');
  EpsilonMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EpsilonMatcher');
  EndGroupMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EndGroupMatcher');
  Target = temper.type('Target');
  Target.methods.eq = function(this__35, other__129)
    local return__63, i__131;
    ::continue_3::
    if not (temper.listed_length(other__129.name) == temper.listed_length(this__35.name__126)) then
      return__63 = false;
      goto break_2;
    end
    i__131 = 0;
    while (i__131 < temper.listed_length(this__35.name__126)) do
      if not (temper.listed_get(other__129.name, i__131) == temper.listed_get(this__35.name__126, i__131)) then
        return__63 = false;
        goto break_2;
      end
      i__131 = temper.int32_add(i__131, 1);
    end
    do
      return true;
    end
    ::break_2::return return__63;
  end;
  Target.methods.toString = function(this__36)
    local return__64, fn__428, body__134;
    ::continue_9::
    if (temper.listed_length(this__36.name__126) == 0) then
      return__64 = '<error>';
      goto break_8;
    end
    fn__428 = function(x__135)
      return temper.concat('v', temper.int32_tostring(x__135));
    end;
    body__134 = temper.listed_join(this__36.name__126, ',', fn__428);
    do
      return temper.concat('{', body__134, '}');
    end
    ::break_8::return return__64;
  end;
  Target.methods.has = function(this__37, id__138)
    local return__66, i__140;
    ::continue_11::i__140 = 0;
    while (i__140 < temper.listed_length(this__37.name__126)) do
      if (temper.listed_get(this__37.name__126, i__140) == id__138) then
        return__66 = true;
        goto break_10;
      end
      i__140 = temper.int32_add(i__140, 1);
    end
    do
      return false;
    end
    ::break_10::return return__66;
  end;
  Target.methods.and_ = function(this__38, other__142)
    local r__144, j__145;
    r__144 = temper.listbuilder_constructor();
    j__145 = 0;
    while (j__145 < temper.listed_length(this__38.name__126)) do
      local exist__146;
      exist__146 = temper.listed_get(this__38.name__126, j__145);
      if not (exist__146 == other__142) then
        temper.listbuilder_add(r__144, exist__146);
      end
      j__145 = temper.int32_add(j__145, 1);
    end
    temper.listbuilder_add(r__144, other__142);
    return Target(r__144);
  end;
  Target.methods.ands = function(this__39, other__148)
    local ret__150, i__151;
    ret__150 = Target(this__39.name__126);
    i__151 = 0;
    while (i__151 < temper.listed_length(other__148.name)) do
      ret__150 = ret__150:and_(temper.listed_get(other__148.name, i__151));
      i__151 = temper.int32_add(i__151, 1);
    end
    return ret__150;
  end;
  Target.constructor = function(this__61, arg__153)
    local fn__427, t_63;
    fn__427 = function(x__154, y__155)
      return temper.int32_sub(x__154, y__155);
    end;
    t_63 = temper.listed_sorted(arg__153, fn__427);
    this__61.name__126 = t_63;
    this__61.id__127 = -1;
    return nil;
  end;
  Target.get.name = function(this__311)
    return this__311.name__126;
  end;
  Target.get.id = function(this__314)
    return this__314.id__127;
  end;
  Target.set.id = function(this__318, newId__317)
    this__318.id__127 = newId__317;
    return nil;
  end;
  Row = temper.type('Row');
  Row.methods.toString = function(this__40)
    local nameStr__164, fn__426, mapStr__165, elseStr__170;
    nameStr__164 = this__40.name__157:toString();
    fn__426 = function(key__166)
      local t_64, keyStr__168, t_65, local_66, local_67, local_68, valueStr__169;
      if (32 <= key__166) then
        t_65 = (key__166 <= 126);
      else
        t_65 = false;
      end
      if t_65 then
        keyStr__168 = temper.concat('#', chr(key__166));
      else
        keyStr__168 = temper.int32_tostring(key__166);
      end
      local_66, local_67, local_68 = temper.pcall(function()
        t_64 = temper.mapped_get(this__40.map__160, key__166);
      end);
      if local_66 then
      else
        t_64 = temper.bubble();
      end
      valueStr__169 = t_64:toString();
      return temper.concat('\n', '  ', keyStr__168, ' => ', valueStr__169);
    end;
    mapStr__165 = temper.listed_join(this__40.keys__159, '', fn__426);
    elseStr__170 = temper.concat('\n', '  else => ', this__40.other__158:toString());
    return temper.concat(nameStr__164, ':', mapStr__165, elseStr__170);
  end;
  Row.constructor = function(this__70, name__172, other__173, keys__174, map__175, ok__176)
    this__70.name__157 = name__172;
    this__70.other__158 = other__173;
    this__70.keys__159 = keys__174;
    this__70.map__160 = map__175;
    this__70.ok__161 = ok__176;
    return nil;
  end;
  Row.get.name = function(this__321)
    return this__321.name__157;
  end;
  Row.get.other = function(this__324)
    return this__324.other__158;
  end;
  Row.get.keys = function(this__327)
    return this__327.keys__159;
  end;
  Row.get.map = function(this__330)
    return this__330.map__160;
  end;
  Row.get.ok = function(this__333)
    return this__333.ok__161;
  end;
  Table = temper.type('Table');
  Table.get.targets = function(this__42)
    return this__42.targetsVar__180;
  end;
  Table.methods.update = function(this__43, t__182)
    local return__81, j__184;
    ::continue_13::return__81 = nil;
    if (temper.listed_length(t__182.name) == 0) then
      t__182['id'] = -1;
      goto break_12;
    end
    j__184 = 0;
    while (j__184 < temper.listed_length(this__43.targets)) do
      if temper.listed_get(this__43.targets, j__184):eq(t__182) then
        t__182['id'] = j__184;
        goto break_12;
      end
      j__184 = temper.int32_add(j__184, 1);
    end
    temper.bubble();
    ::break_12::return return__81;
  end;
  Table.methods.finish = function(this__44)
    local build__187, t_70, i__188, i__192;
    build__187 = temper.listbuilder_constructor();
    t_70 = build__187;
    this__44.targetsVar__180 = t_70;
    i__188 = 0;
    while (i__188 < temper.listbuilder_length(this__44.rows__177)) do
      local row__190, j__191, t_72;
      ::continue_15::row__190 = temper.listed_get(this__44.rows__177, i__188);
      j__191 = 0;
      while (j__191 < temper.listbuilder_length(build__187)) do
        if temper.listed_get(build__187, j__191):eq(row__190.name) then
          local t_71;
          t_71 = j__191;
          (row__190.name)['id'] = t_71;
          goto break_14;
        end
        j__191 = temper.int32_add(j__191, 1);
      end
      t_72 = i__188;
      (row__190.name)['id'] = t_72;
      temper.listbuilder_add(build__187, row__190.name);
      ::break_14::i__188 = temper.int32_add(i__188, 1);
    end
    i__192 = 0;
    while (i__192 < temper.listbuilder_length(this__44.rows__177)) do
      local row__193, j__194;
      row__193 = temper.listed_get(this__44.rows__177, i__192);
      this__44:update(row__193.other);
      j__194 = 0;
      while (j__194 < temper.listed_length(row__193.keys)) do
        local key__196, cur__197, local_73, local_74, local_75;
        key__196 = temper.listed_get(row__193.keys, j__194);
        local_73, local_74, local_75 = temper.pcall(function()
          cur__197 = temper.mapped_get(row__193.map, key__196);
        end);
        if local_73 then
        else
          cur__197 = temper.bubble();
        end
        this__44:update(cur__197);
        j__194 = temper.int32_add(j__194, 1);
      end
      i__192 = temper.int32_add(i__192, 1);
    end
    return nil;
  end;
  Table.methods.has = function(this__45, name__199)
    local return__83, i__201;
    ::continue_17::i__201 = 0;
    while (i__201 < temper.listbuilder_length(this__45.rows__177)) do
      if (temper.listed_get(this__45.rows__177, i__201)).name:eq(name__199) then
        return__83 = true;
        goto break_16;
      end
      i__201 = temper.int32_add(i__201, 1);
    end
    do
      return false;
    end
    ::break_16::return return__83;
  end;
  Table.methods.add = function(this__46, row__203)
    if not this__46:has(row__203.name) then
      temper.listbuilder_add(this__46.rows__177, row__203);
    end
    return nil;
  end;
  Table.methods.addEmpty = function(this__47, name__206)
    this__47:add(Row(name__206, noTarget, temper.listof(), temper.map_constructor(temper.listof()), false));
    return nil;
  end;
  Table.methods.toString = function(this__48)
    local fn__425;
    fn__425 = function(s__210)
      return s__210:toString();
    end;
    return temper.listed_join(this__48.rows__177, '\n', fn__425);
  end;
  Table.methods.scan = function(this__49, str__213, head__214)
    local cur__216;
    cur__216 = temper.listed_get(this__49.rows__177, 0);
    while true do
      local t_77;
      if not cur__216.ok then
        t_77 = temper.string_hasindex(str__213, head__214);
      else
        t_77 = false;
      end
      if not t_77 then
        break;
      end
      cur__216 = temper.listed_get(this__49.rows__177, (temper.mapped_getor(cur__216.map, temper.string_get(str__213, head__214), cur__216.other)).id);
      head__214 = temper.string_next(str__213, head__214);
    end
    if cur__216.ok then
      return head__214;
    else
      return -1.0;
    end
  end;
  Table.constructor = function(this__77, rows__218, targets__219)
    this__77.rows__177 = rows__218;
    this__77.targetsVar__180 = targets__219;
    return nil;
  end;
  Table.get.rows = function(this__336)
    return this__336.rows__177;
  end;
  Alphabet = temper.type('Alphabet');
  Alphabet.methods.add = function(this__52, key__223)
    if not temper.mapped_getor(this__52.map__221, key__223, false) then
      temper.listbuilder_add(this__52.keys__220, key__223);
      temper.mapbuilder_set(this__52.map__221, key__223, true);
    end
    return nil;
  end;
  Alphabet.fromEngine = function(engine__226)
    local ret__228, i__229;
    ret__228 = Alphabet();
    i__229 = 0;
    while (i__229 < temper.listbuilder_length(engine__226.states)) do
      local state__230, j__231;
      state__230 = temper.listed_get(engine__226.states, i__229);
      j__231 = 0;
      while (j__231 < temper.listbuilder_length(state__230.next)) do
        local t_78, matcher__232;
        t_78 = temper.listed_get(state__230.next, j__231);
        matcher__232 = t_78.matcher;
        if temper.instance_of(matcher__232, ContainsMatcher) then
          local t_79, k__233;
          t_79 = temper.cast_to(matcher__232, ContainsMatcher);
          k__233 = 0;
          while (k__233 < temper.listed_length(t_79.options)) do
            ret__228:add(temper.listed_get(t_79.options, k__233));
            k__233 = temper.int32_add(k__233, 1);
          end
        elseif temper.instance_of(matcher__232, SingleMatcher) then
          ret__228:add((temper.cast_to(matcher__232, SingleMatcher)).char);
        end
        j__231 = temper.int32_add(j__231, 1);
      end
      i__229 = temper.int32_add(i__229, 1);
    end
    return ret__228;
  end;
  Alphabet.constructor = function(this__89)
    local t_80, t_81;
    t_80 = temper.listbuilder_constructor();
    this__89.keys__220 = t_80;
    t_81 = temper.mapbuilder_constructor();
    this__89.map__221 = t_81;
    return nil;
  end;
  Alphabet.get.keys = function(this__339)
    return this__339.keys__220;
  end;
  Alphabet.get.map = function(this__342)
    return this__342.map__221;
  end;
  DFA = temper.type('DFA');
  DFA.none = function()
    return DFA(noTable);
  end;
  DFA.fromEngine = function(engine__248)
    local return__98, alphabet__250, states__251, i__252, newTable__254, start__255, ok__256, i__257;
    ::continue_19::alphabet__250 = Alphabet.fromEngine(engine__248);
    states__251 = temper.mapbuilder_constructor();
    i__252 = 0;
    while (i__252 < temper.listbuilder_length(engine__248.states)) do
      local state__253;
      state__253 = temper.listed_get(engine__248.states, i__252);
      temper.mapbuilder_set(states__251, state__253.id, state__253);
      i__252 = temper.int32_add(i__252, 1);
    end
    newTable__254 = Table(temper.listbuilder_constructor(), temper.listof());
    start__255 = epsilonClosure(engine__248.startState, noTarget);
    newTable__254:addEmpty(start__255);
    ok__256 = true;
    i__257 = 0;
    while (i__257 < temper.listbuilder_length(newTable__254.rows)) do
      local t_82, row__261, other__262, keys__263, map__264, addOther__258, add__259, onState__260, j__285, j__286, t_101, t_102;
      if (i__257 == 500) then
        return__98 = temper.null;
        goto break_18;
      end
      row__261 = temper.listed_get(newTable__254.rows, i__257);
      other__262 = Target(temper.listof());
      keys__263 = temper.listbuilder_constructor();
      map__264 = temper.mapbuilder_constructor();
      addOther__258 = function(toState__265)
        local return__99, j__267;
        ::continue_21::return__99 = nil;
        if other__262:has(toState__265.id) then
          goto break_20;
        end
        other__262 = other__262:and_(toState__265.id);
        j__267 = 0;
        while (j__267 < temper.listbuilder_length(toState__265.next)) do
          local t_83, dest__268, matcher__269;
          t_83 = temper.listed_get(toState__265.next, j__267);
          dest__268 = t_83.toState;
          matcher__269 = t_83.matcher;
          if isEmptyEdge(matcher__269) then
            addOther__258(dest__268);
          else
            local t_84;
            if temper.instance_of(matcher__269, WordBoundaryMatcher) then
              t_84 = true;
            elseif temper.instance_of(matcher__269, BeginMatcher) then
              t_84 = true;
            else
              t_84 = temper.instance_of(matcher__269, EndMatcher);
            end
            if t_84 then
              ok__256 = false;
            end
          end
          j__267 = temper.int32_add(j__267, 1);
        end
        ::break_20::return return__99;
      end;
      add__259 = function(char__270, toState__271)
        local return__100, existing__273, j__274;
        ::continue_23::return__100 = nil;
        existing__273 = temper.mapped_getor(map__264, char__270, noTarget);
        if existing__273:has(toState__271.id) then
          goto break_22;
        end
        if not temper.mapped_has(map__264, char__270) then
          temper.listbuilder_add(keys__263, char__270);
        end
        temper.mapbuilder_set(map__264, char__270, existing__273:and_(toState__271.id));
        j__274 = 0;
        while (j__274 < temper.listbuilder_length(toState__271.next)) do
          local t_85, dest__275, matcher__276;
          t_85 = temper.listed_get(toState__271.next, j__274);
          dest__275 = t_85.toState;
          matcher__276 = t_85.matcher;
          if isEmptyEdge(matcher__276) then
            add__259(char__270, dest__275);
          else
            local t_86;
            if temper.instance_of(matcher__276, WordBoundaryMatcher) then
              t_86 = true;
            elseif temper.instance_of(matcher__276, BeginMatcher) then
              t_86 = true;
            else
              t_86 = temper.instance_of(matcher__276, EndMatcher);
            end
            if t_86 then
              ok__256 = false;
            end
          end
          j__274 = temper.int32_add(j__274, 1);
        end
        ::break_22::return return__100;
      end;
      onState__260 = function(state__277)
        local j__279;
        j__279 = 0;
        while (j__279 < temper.listbuilder_length(state__277.next)) do
          local t_87, toState__280, matcher__281, t_88;
          t_87 = temper.listed_get(state__277.next, j__279);
          toState__280 = t_87.toState;
          matcher__281 = t_87.matcher;
          if temper.instance_of(matcher__281, ContainsMatcher) then
            local t_89;
            t_89 = temper.cast_to(matcher__281, ContainsMatcher);
            if t_89.want then
              local k__282;
              k__282 = 0;
              while (k__282 < temper.listed_length(t_89.options)) do
                add__259(temper.listed_get(t_89.options, k__282), toState__280);
                k__282 = temper.int32_add(k__282, 1);
              end
            else
              local k__283;
              k__283 = 0;
              while (k__283 < temper.listbuilder_length(alphabet__250.keys)) do
                local chr__284;
                chr__284 = temper.listed_get(alphabet__250.keys, k__283);
                if not temper.densebitvector_get(t_89.bitset, chr__284) then
                  add__259(chr__284, toState__280);
                elseif not temper.mapped_has(map__264, chr__284) then
                  temper.mapbuilder_set(map__264, chr__284, Target(temper.listof()));
                  temper.listbuilder_add(keys__263, chr__284);
                end
                k__283 = temper.int32_add(k__283, 1);
              end
              addOther__258(toState__280);
            end
          elseif temper.instance_of(matcher__281, SingleMatcher) then
            t_88 = temper.cast_to(matcher__281, SingleMatcher);
            add__259(t_88.char, toState__280);
          else
            local t_90;
            if temper.instance_of(matcher__281, WordBoundaryMatcher) then
              t_90 = true;
            elseif temper.instance_of(matcher__281, BeginMatcher) then
              t_90 = true;
            else
              t_90 = temper.instance_of(matcher__281, EndMatcher);
            end
            if t_90 then
              ok__256 = false;
            end
          end
          j__279 = temper.int32_add(j__279, 1);
        end
        return nil;
      end;
      j__285 = 0;
      while (j__285 < temper.listed_length(row__261.name.name)) do
        local t_91, local_92, local_93, local_94;
        local_92, local_93, local_94 = temper.pcall(function()
          t_91 = temper.mapped_get(states__251, temper.listed_get(row__261.name.name, j__285));
        end);
        if local_92 then
        else
          t_91 = temper.bubble();
        end
        onState__260(t_91);
        j__285 = temper.int32_add(j__285, 1);
      end
      other__262 = other__262:ands(start__255);
      newTable__254:addEmpty(other__262);
      j__286 = 0;
      while (j__286 < temper.listbuilder_length(keys__263)) do
        local t_96, key__287, local_97, local_98, local_99, val__288;
        key__287 = temper.listed_get(keys__263, j__286);
        local_97, local_98, local_99 = temper.pcall(function()
          t_96 = temper.mapped_get(map__264, key__287);
        end);
        if local_97 then
        else
          t_96 = temper.bubble();
        end
        val__288 = t_96:ands(start__255);
        temper.mapbuilder_set(map__264, key__287, val__288);
        newTable__254:addEmpty(val__288);
        j__286 = temper.int32_add(j__286, 1);
      end
      t_101 = row__261.name;
      t_102 = other__262;
      if (temper.listed_getor(row__261.name.name, 0, -1) == 1) then
        t_82 = true;
      else
        t_82 = (temper.listed_getor(row__261.name.name, 1, -1) == 1);
      end
      temper.listbuilder_set(newTable__254.rows, i__257, Row(t_101, t_102, keys__263, map__264, t_82));
      i__257 = temper.int32_add(i__257, 1);
    end
    newTable__254:finish();
    if not ok__256 then
      return temper.null;
    else
      return DFA(newTable__254);
    end
    ::break_18::return return__98;
  end;
  DFA.methods.found = function(this__55, s__290, start__291)
    return this__55.table__244:scan(s__290, start__291);
  end;
  DFA.constructor = function(this__95, table__294)
    this__95.table__244 = table__294;
    return nil;
  end;
  DFA.get.table = function(this__347)
    return this__347.table__244;
  end;
  noTarget = Target(temper.listof());
  noTable = Table(temper.listbuilder_constructor(), temper.listof());
  isEmptyEdge = function(matcher__235)
    if temper.instance_of(matcher__235, EpsilonMatcher) then
      return true;
    elseif temper.instance_of(matcher__235, StartGroupMatcher) then
      return true;
    else
      return temper.instance_of(matcher__235, EndGroupMatcher);
    end
  end;
  epsilonClosure = function(state__237, target__238)
    local return__94, ret__240, j__241;
    ::continue_25::
    if target__238:has(state__237.id) then
      return__94 = target__238;
      goto break_24;
    end
    ret__240 = target__238:and_(state__237.id);
    j__241 = 0;
    while (j__241 < temper.listbuilder_length(state__237.next)) do
      local t_103, toState__242, matcher__243;
      t_103 = temper.listed_get(state__237.next, j__241);
      toState__242 = t_103.toState;
      matcher__243 = t_103.matcher;
      if isEmptyEdge(matcher__243) then
        ret__240 = epsilonClosure(toState__242, ret__240);
      end
      j__241 = temper.int32_add(j__241, 1);
    end
    do
      return ret__240;
    end
    ::break_24::return return__94;
  end;
  exports = {};
  exports.Target = Target;
  exports.Row = Row;
  exports.Table = Table;
  exports.DFA = DFA;
  return exports;
end
package.preload['temper-regex-engine/dfa/javascript'] = function()
  local temper = require('temper-core');
  local Javascript, exports;
  Javascript = temper.type('Javascript');
  Javascript.fromTable = function(table__19)
    local r__21, fn__46;
    r__21 = Javascript(temper.listbuilder_constructor());
    r__21:addTable(table__19);
    fn__46 = function(s__22)
      return s__22;
    end;
    return temper.listed_join(r__21.buf, '', fn__46);
  end;
  Javascript.methods.addString = function(this__2, string__25)
    temper.listbuilder_add(this__2.buf__17, string__25);
    return nil;
  end;
  Javascript.methods.addRow = function(this__3, row__28)
    local id__30;
    id__30 = temper.int32_tostring(row__28.name.id);
    this__3:addString(temper.concat('rows[', id__30, '] = (code) => {', '\n'));
    if not (temper.listed_length(row__28.keys) == 0) then
      local i__31;
      this__3:addString('  switch (code) {\n');
      i__31 = 0;
      while (i__31 < temper.listed_length(row__28.keys)) do
        local key__32, target__33, local_108, local_109, local_110;
        key__32 = temper.listed_get(row__28.keys, i__31);
        local_108, local_109, local_110 = temper.pcall(function()
          target__33 = temper.mapped_get(row__28.map, key__32);
        end);
        if local_108 then
        else
          target__33 = temper.bubble();
        end
        this__3:addString(temper.concat('  case ', temper.int32_tostring(key__32), ': return ', temper.int32_tostring(target__33.id), ';', '\n'));
        i__31 = temper.int32_add(i__31, 1);
      end
      this__3:addString('  }\n');
    end
    this__3:addString(temper.concat('  return ', temper.int32_tostring(row__28.other.id), ';', '\n'));
    this__3:addString('};\n');
    if row__28.ok then
      this__3:addString(temper.concat('ok[', id__30, '] = true;', '\n'));
    end
    return nil;
  end;
  Javascript.methods.addTable = function(this__4, table__35)
    local i__37;
    this__4:addString('const rows = [];\n');
    this__4:addString('const ok = [];\n');
    i__37 = 0;
    while (i__37 < temper.listbuilder_length(table__35.rows)) do
      this__4:addRow(temper.listed_get(table__35.rows, i__37));
      i__37 = temper.int32_add(i__37, 1);
    end
    this__4:addString('module.ret = (str, head) => {\n');
    this__4:addString('  let row = 0;\n');
    this__4:addString('  while (!ok[row]) {\n');
    this__4:addString('    if (head >= str.length) return -1;\n');
    this__4:addString('    const code = str.codePointAt(head);\n');
    this__4:addString('    head += code > 0xffff ? 2 : 1;\n');
    this__4:addString('    row = rows[row](code);\n');
    this__4:addString('  }\n');
    this__4:addString('  return head;\n');
    this__4:addString('};\n');
    return nil;
  end;
  Javascript.constructor = function(this__5, buf__39)
    this__5.buf__17 = buf__39;
    return nil;
  end;
  Javascript.get.buf = function(this__42)
    return this__42.buf__17;
  end;
  exports = {};
  exports.Javascript = Javascript;
  return exports;
end
package.preload['temper-regex-engine/dfa/lua'] = function()
  local temper = require('temper-core');
  local Lua, exports;
  Lua = temper.type('Lua');
  Lua.fromTable = function(table__20)
    local r__22, fn__47;
    r__22 = Lua(temper.listbuilder_constructor());
    r__22:addTable(table__20);
    fn__47 = function(s__23)
      return s__23;
    end;
    return temper.listed_join(r__22.buf, '', fn__47);
  end;
  Lua.methods.addString = function(this__3, string__26)
    temper.listbuilder_add(this__3.buf__18, string__26);
    return nil;
  end;
  Lua.methods.addRow = function(this__4, row__29)
    local id__31, i__32;
    id__31 = temper.int32_tostring(row__29.name.id);
    this__4:addString(temper.concat('rows[', id__31, '] = function(code)', '\n'));
    i__32 = 0;
    while (i__32 < temper.listed_length(row__29.keys)) do
      local key__33, target__34, local_104, local_105, local_106;
      key__33 = temper.listed_get(row__29.keys, i__32);
      local_104, local_105, local_106 = temper.pcall(function()
        target__34 = temper.mapped_get(row__29.map, key__33);
      end);
      if local_104 then
      else
        target__34 = temper.bubble();
      end
      if (i__32 == 0) then
        this__4:addString('  if ');
      else
        this__4:addString('  elseif ');
      end
      this__4:addString(temper.concat('code == ', temper.int32_tostring(key__33), ' then return ', temper.int32_tostring(target__34.id), '\n'));
      i__32 = temper.int32_add(i__32, 1);
    end
    if not (temper.listed_length(row__29.keys) == 0) then
      this__4:addString('  end\n');
    end
    this__4:addString(temper.concat('  return ', temper.int32_tostring(row__29.other.id), '\n'));
    this__4:addString('end\n');
    if row__29.ok then
      this__4:addString(temper.concat('ok[', id__31, '] = true', '\n'));
    end
    return nil;
  end;
  Lua.methods.addTable = function(this__5, table__36)
    local i__38;
    this__5:addString('local utf8_codepoint = utf8.codepoint\n');
    this__5:addString('local utf8_offset = utf8.offset\n');
    this__5:addString('local rows = {}\n');
    this__5:addString('local ok = {}\n');
    i__38 = 0;
    while (i__38 < temper.listbuilder_length(table__36.rows)) do
      this__5:addRow(temper.listed_get(table__36.rows, i__38));
      i__38 = temper.int32_add(i__38, 1);
    end
    this__5:addString('return function(str, head)\n');
    this__5:addString('  local row = 0\n');
    this__5:addString('  while not ok[row] do\n');
    this__5:addString('    if head > #str then return -1 end\n');
    this__5:addString('    local code = utf8_codepoint(str, head)\n');
    this__5:addString('    head = utf8_offset(str, 2, head)\n');
    this__5:addString('    row = rows[row](code)\n');
    this__5:addString('  end\n');
    this__5:addString('  return head\n');
    this__5:addString('end\n');
    return nil;
  end;
  Lua.constructor = function(this__6, buf__40)
    this__6.buf__18 = buf__40;
    return nil;
  end;
  Lua.get.buf = function(this__43)
    return this__43.buf__18;
  end;
  exports = {};
  exports.Lua = Lua;
  return exports;
end
package.preload['temper-regex-engine/nfa/engine'] = function()
  local temper = require('temper-core');
  local entire, oneOrMore, optional, Begin, Dot, End, WordBoundary, Digit, Space, Word, charCodeMatcher, fromChars, regexToString, chr, bogusMatch, State, StackSlot, EndGroupMatcher, StartGroupMatcher, Span, EpsilonMatcher, Group, Match, Hit, Engine, Marks, closeSpan, buildMatch, exports;
  entire = temper.import('std/regex', 'entire');
  oneOrMore = temper.import('std/regex', 'oneOrMore');
  optional = temper.import('std/regex', 'optional');
  Begin = temper.import('std/regex', 'Begin');
  Dot = temper.import('std/regex', 'Dot');
  End = temper.import('std/regex', 'End');
  WordBoundary = temper.import('std/regex', 'WordBoundary');
  Digit = temper.import('std/regex', 'Digit');
  Space = temper.import('std/regex', 'Space');
  Word = temper.import('std/regex', 'Word');
  charCodeMatcher = temper.import('temper-regex-engine/nfa/matcher', 'charCodeMatcher');
  fromChars = temper.import('temper-regex-engine/nfa/matcher', 'fromChars');
  regexToString = temper.import('temper-regex-engine/util', 'regexToString');
  chr = temper.import('temper-regex-engine/util', 'chr');
  bogusMatch = temper.import('temper-regex-engine/util', 'bogusMatch');
  State = temper.import('temper-regex-engine/nfa/state', 'State');
  StackSlot = temper.import('temper-regex-engine/nfa/stack', 'StackSlot');
  EndGroupMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EndGroupMatcher');
  StartGroupMatcher = temper.import('temper-regex-engine/nfa/matcher', 'StartGroupMatcher');
  Span = temper.import('temper-regex-engine/nfa/stack', 'Span');
  EpsilonMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EpsilonMatcher');
  Group = temper.import('std/regex', 'Group');
  Match = temper.import('std/regex', 'Match');
  Hit = temper.type('Hit');
  Hit.constructor = function(this__33, begin__109, end__110, spans__111)
    if (spans__111 == nil) then
      spans__111 = temper.null;
    end
    this__33.begin__105 = begin__109;
    this__33.end__106 = end__110;
    this__33.spans__107 = spans__111;
    return nil;
  end;
  Hit.get.begin = function(this__225)
    return this__225.begin__105;
  end;
  Hit.get.end_ = function(this__228)
    return this__228.end__106;
  end;
  Hit.get.spans = function(this__231)
    return this__231.spans__107;
  end;
  Engine = temper.type('Engine');
  Engine.newStateIn = function(states__123)
    local ret__125;
    ret__125 = State(temper.listbuilder_length(states__123));
    temper.listbuilder_add(states__123, ret__125);
    return ret__125;
  end;
  Engine.methods.newState = function(this__18)
    return Engine.newStateIn(this__18.states__116);
  end;
  Engine.methods.addGroupName = function(this__19, name__129)
    local return__41, i__131;
    ::continue_3::return__41 = nil;
    i__131 = 0;
    while (i__131 < temper.listbuilder_length(this__19.groupNames__120)) do
      if (temper.listed_get(this__19.groupNames__120, i__131) == name__129) then
        goto break_2;
      end
      i__131 = temper.int32_add(i__131, 1);
    end
    temper.listbuilder_add(this__19.groupNames__120, name__129);
    ::break_2::return return__41;
  end;
  Engine.methods.search = function(this__20, str__133, from__134)
    local scanner__136, t_39;
    scanner__136 = this__20.scan__121;
    if temper.is_null(scanner__136) then
      t_39 = true;
    else
      t_39 = not (scanner__136(str__133, from__134) == -1.0);
    end
    if t_39 then
      return this__20:searchFrom(str__133, from__134);
    else
      return temper.null;
    end
  end;
  Engine.methods.searchFrom = function(this__21, str__138, from__139)
    local return__43, marks__141, stack__142, begin__143, beginPos__144;
    ::continue_5::marks__141 = Marks(temper.listbuilder_length(this__21.states__116));
    stack__142 = temper.listbuilder_constructor();
    begin__143 = from__139;
    beginPos__144 = 0;
    while true do
      temper.listbuilder_add(stack__142, StackSlot(begin__143, beginPos__144, this__21.startState__117, temper.null));
      while not temper.listed_isempty(stack__142) do
        local top__145, t_40;
        top__145 = temper.listbuilder_removelast(stack__142);
        if (top__145.state.incoming < 2) then
          t_40 = true;
        else
          t_40 = marks__141:mark(top__145.state, top__145.pos);
        end
        if t_40 then
          local index__146, next__147, c__148;
          if (top__145.state.id == this__21.endState__118.id) then
            return__43 = Hit(begin__143, top__145.index, top__145.spans);
            goto break_4;
          end
          index__146 = top__145.index;
          next__147 = top__145.state.next;
          c__148 = temper.int32_sub(temper.listbuilder_length(next__147), 1);
          while (c__148 >= 0) do
            local t_41, matcher__149, toState__150, skip__151;
            t_41 = temper.listed_get(next__147, c__148);
            matcher__149 = t_41.matcher;
            toState__150 = t_41.toState;
            skip__151 = matcher__149:matches(str__138, index__146);
            if temper.is_string_index(skip__151) then
              local t_42, pos__152;
              t_42 = temper.require_string_index(skip__151);
              if (t_42 == index__146) then
                pos__152 = top__145.pos;
              else
                pos__152 = temper.int32_add(top__145.pos, 1);
              end
              if not marks__141:has(toState__150, pos__152) then
                local spans__153, t_43;
                if temper.instance_of(matcher__149, StartGroupMatcher) then
                  spans__153 = Span((temper.cast_to(matcher__149, StartGroupMatcher)).name, index__146, temper.null, top__145.spans);
                elseif temper.instance_of(matcher__149, EndGroupMatcher) then
                  t_43 = temper.cast_to(matcher__149, EndGroupMatcher);
                  spans__153 = closeSpan(top__145.spans, t_43.name, index__146);
                else
                  spans__153 = top__145.spans;
                end
                temper.listbuilder_add(stack__142, StackSlot(t_42, pos__152, toState__150, spans__153));
              end
            end
            c__148 = temper.int32_sub(c__148, 1);
          end
        end
      end
      if not temper.string_hasindex(str__138, begin__143) then
        break;
      end
      begin__143 = temper.string_next(str__138, begin__143);
      beginPos__144 = temper.int32_add(beginPos__144, 1);
    end
    do
      return temper.null;
    end
    ::break_4::return return__43;
  end;
  Engine.methods.replace = function(this__22, str__155, replacer__156)
    local ret__158, last__159, from__160;
    ret__158 = temper.stringbuilder_constructor();
    last__159 = 1.0;
    from__160 = 1.0;
    while true do
      local hit__161;
      hit__161 = this__22:search(str__155, from__160);
      if not temper.is_null(hit__161) then
        local hit_44;
        hit_44 = hit__161;
        temper.stringbuilder_appendbetween(ret__158, str__155, last__159, hit_44.begin);
        temper.stringbuilder_append(ret__158, replacer__156(buildMatch(str__155, hit_44, this__22.groupNames__120)));
        last__159 = hit_44.end_;
        if not (hit_44.end_ == hit_44.begin) then
          from__160 = hit_44.end_;
        elseif temper.string_hasindex(str__155, hit_44.end_) then
          from__160 = temper.string_next(str__155, hit_44.end_);
        else
          break;
        end
      else
        break;
      end
    end
    temper.stringbuilder_appendbetween(ret__158, str__155, last__159, temper.string_end(str__155));
    return temper.stringbuilder_tostring(ret__158);
  end;
  Engine.methods.split = function(this__23, str__163)
    local ret__165, last__166, from__167;
    ret__165 = temper.listbuilder_constructor();
    last__166 = 1.0;
    from__167 = 1.0;
    while true do
      local hit__168;
      hit__168 = this__23:search(str__163, from__167);
      if not temper.is_null(hit__168) then
        local hit_45;
        hit_45 = hit__168;
        if not temper.string_hasindex(str__163, hit_45.begin) then
          break;
        end
        if (hit_45.end_ == last__166) then
          from__167 = temper.string_next(str__163, hit_45.begin);
        else
          local t_46;
          temper.listbuilder_add(ret__165, temper.string_slice(str__163, last__166, hit_45.begin));
          last__166 = hit_45.end_;
          if (hit_45.end_ == hit_45.begin) then
            t_46 = temper.string_next(str__163, hit_45.end_);
          else
            t_46 = hit_45.end_;
          end
          from__167 = t_46;
        end
      else
        break;
      end
    end
    temper.listbuilder_add(ret__165, temper.string_slice(str__163, last__166, temper.string_end(str__163)));
    return ret__165;
  end;
  Engine.methods.found = function(this__24, str__170, start__171)
    local scanner__173;
    scanner__173 = this__24.scan__121;
    if not temper.is_null(scanner__173) then
      return not (scanner__173(str__170, start__171) == -1.0);
    else
      return not temper.is_null(this__24:searchFrom(str__170, start__171));
    end
  end;
  Engine.methods.find = function(this__25, str__175, begin__257)
    local begin__176, hit__178;
    if temper.is_null(begin__257) then
      begin__176 = 1.0;
    else
      begin__176 = begin__257;
    end
    hit__178 = this__25:search(str__175, begin__176);
    if not temper.is_null(hit__178) then
      local hit_47;
      hit_47 = hit__178;
      return buildMatch(str__175, hit_47, this__25.groupNames__120);
    else
      return bogusMatch();
    end
  end;
  Engine.constructor = function(this__37)
    local t_48, t_49, t_50, t_51, t_52;
    t_48 = temper.listbuilder_constructor();
    this__37.states__116 = t_48;
    t_49 = Engine.newStateIn(this__37.states__116);
    this__37.startState__117 = t_49;
    t_50 = Engine.newStateIn(this__37.states__116);
    this__37.endState__118 = t_50;
    t_51 = EpsilonMatcher();
    this__37.epsilon__119 = t_51;
    t_52 = temper.listbuilder_constructor();
    this__37.groupNames__120 = t_52;
    this__37.scan__121 = temper.null;
    return nil;
  end;
  Engine.get.states = function(this__234)
    return this__234.states__116;
  end;
  Engine.get.startState = function(this__237)
    return this__237.startState__117;
  end;
  Engine.get.endState = function(this__240)
    return this__240.endState__118;
  end;
  Engine.get.epsilon = function(this__243)
    return this__243.epsilon__119;
  end;
  Engine.get.groupNames = function(this__246)
    return this__246.groupNames__120;
  end;
  Engine.get.scan = function(this__249)
    return this__249.scan__121;
  end;
  Engine.set.scan = function(this__253, newScan__252)
    if (newScan__252 == nil) then
      newScan__252 = temper.null;
    end
    this__253.scan__121 = newScan__252;
    return nil;
  end;
  Marks = temper.type('Marks');
  Marks.methods.mark = function(this__29, state__183, pos__184)
    local bits__187, fresh__188;
    if temper.listed_isempty(this__29.byState__181) then
      local i__186;
      i__186 = 0;
      while (i__186 < this__29.stateCount__180) do
        temper.listbuilder_add(this__29.byState__181, temper.densebitvector_constructor(0));
        i__186 = temper.int32_add(i__186, 1);
      end
    end
    bits__187 = temper.listed_get(this__29.byState__181, state__183.id);
    fresh__188 = not temper.densebitvector_get(bits__187, pos__184);
    temper.densebitvector_set(bits__187, pos__184, true);
    return fresh__188;
  end;
  Marks.methods.has = function(this__30, state__190, pos__191)
    if not temper.listed_isempty(this__30.byState__181) then
      return temper.densebitvector_get(temper.listed_get(this__30.byState__181, state__190.id), pos__191);
    else
      return false;
    end
  end;
  Marks.constructor = function(this__48, stateCount__194)
    local t_53;
    this__48.stateCount__180 = stateCount__194;
    t_53 = temper.listbuilder_constructor();
    this__48.byState__181 = t_53;
    return nil;
  end;
  closeSpan = function(spans__195, name__196, end__197)
    local return__52, rest__199;
    if (spans__195 == nil) then
      spans__195 = temper.null;
    end
    ::continue_7::rest__199 = spans__195;
    while true do
      local span__200;
      span__200 = rest__199;
      if not temper.is_null(span__200) then
        local span_54, t_55;
        span_54 = span__200;
        if temper.is_null(span_54.end_) then
          t_55 = (span_54.name == name__196);
        else
          t_55 = false;
        end
        if t_55 then
          return__52 = Span(name__196, span_54.begin, end__197, spans__195);
          goto break_6;
        end
        rest__199 = span_54.rest;
      else
        break;
      end
    end
    do
      return temper.bubble();
    end
    ::break_6::return return__52;
  end;
  buildMatch = function(str__201, hit__202, names__203)
    local latest__205, rest__206, groups__209, i__210, full__212;
    latest__205 = temper.mapbuilder_constructor();
    rest__206 = hit__202.spans;
    while true do
      local span__207;
      span__207 = rest__206;
      if not temper.is_null(span__207) then
        local span_56, end__208;
        span_56 = span__207;
        end__208 = span_56.end_;
        if not temper.is_null(end__208) then
          local end_57;
          end_57 = end__208;
          if not temper.mapped_has(latest__205, span_56.name) then
            temper.mapbuilder_set(latest__205, span_56.name, Group(span_56.name, temper.string_slice(str__201, span_56.begin, end_57), span_56.begin, end_57));
          end
        end
        rest__206 = span_56.rest;
      else
        break;
      end
    end
    groups__209 = temper.mapbuilder_constructor();
    i__210 = 0;
    while (i__210 < temper.listed_length(names__203)) do
      local name__211;
      name__211 = temper.listed_get(names__203, i__210);
      if temper.mapped_has(latest__205, name__211) then
        local t_58, local_59, local_60, local_61;
        local_59, local_60, local_61 = temper.pcall(function()
          t_58 = temper.mapped_get(latest__205, name__211);
        end);
        if local_59 then
        else
          t_58 = temper.bubble();
        end
        temper.mapbuilder_set(groups__209, name__211, t_58);
      end
      i__210 = temper.int32_add(i__210, 1);
    end
    full__212 = Group('full', temper.string_slice(str__201, hit__202.begin, hit__202.end_), hit__202.begin, hit__202.end_);
    return Match(full__212, temper.mapped_tomap(groups__209));
  end;
  exports = {};
  exports.Engine = Engine;
  return exports;
end
package.preload['temper-regex-engine/nfa/matcher'] = function()
  local temper = require('temper-core');
  local isWordChar, wordChars, spaceChars, wordCharsList, spaceCharsList, digitCharsList, regexToString, bogusMatch, chr, Matcher, BeginMatcher, EndMatcher, WordBoundaryMatcher, SingleMatcher, ContainsMatcher, StartGroupMatcher, EndGroupMatcher, EpsilonMatcher, noMatch, fromChars, charCodeMatcher, exports;
  isWordChar = temper.import('temper-regex-engine/ctype', 'isWordChar');
  wordChars = temper.import('temper-regex-engine/ctype', 'wordChars');
  spaceChars = temper.import('temper-regex-engine/ctype', 'spaceChars');
  wordCharsList = temper.import('temper-regex-engine/ctype', 'wordCharsList');
  spaceCharsList = temper.import('temper-regex-engine/ctype', 'spaceCharsList');
  digitCharsList = temper.import('temper-regex-engine/ctype', 'digitCharsList');
  regexToString = temper.import('temper-regex-engine/util', 'regexToString');
  bogusMatch = temper.import('temper-regex-engine/util', 'bogusMatch');
  chr = temper.import('temper-regex-engine/util', 'chr');
  Matcher = temper.type('Matcher');
  Matcher.methods.matches = function(this__7, str__80, index__81)
    temper.virtual();
  end;
  Matcher.methods.toString = function(this__8)
    temper.virtual();
  end;
  BeginMatcher = temper.type('BeginMatcher', Matcher);
  BeginMatcher.methods.matches = function(this__9, str__86, index__87)
    if ((index__87 - 1.0) == 0) then
      return index__87;
    else
      return -1.0;
    end
  end;
  BeginMatcher.methods.toString = function(this__10)
    return '^';
  end;
  BeginMatcher.constructor = function(this__33)
    return nil;
  end;
  EndMatcher = temper.type('EndMatcher', Matcher);
  EndMatcher.methods.matches = function(this__11, str__93, index__94)
    if ((index__94 - temper.string_end(str__93)) == 0) then
      return index__94;
    else
      return -1.0;
    end
  end;
  EndMatcher.methods.toString = function(this__12)
    return '$';
  end;
  EndMatcher.constructor = function(this__37)
    return nil;
  end;
  WordBoundaryMatcher = temper.type('WordBoundaryMatcher', Matcher);
  WordBoundaryMatcher.methods.matches = function(this__13, str__100, index__101)
    local t_30, t_31;
    if (index__101 > 1.0) then
      t_30 = isWordChar(temper.string_get(str__100, temper.string_prev(str__100, index__101)));
    else
      t_30 = false;
    end
    if (index__101 < temper.string_end(str__100)) then
      t_31 = isWordChar(temper.string_get(str__100, index__101));
    else
      t_31 = false;
    end
    if not temper.bool_eq(t_30, t_31) then
      return index__101;
    else
      return -1.0;
    end
  end;
  WordBoundaryMatcher.methods.toString = function(this__14)
    return '\\b';
  end;
  WordBoundaryMatcher.constructor = function(this__41)
    return nil;
  end;
  SingleMatcher = temper.type('SingleMatcher', Matcher);
  SingleMatcher.methods.matches = function(this__18, str__113, index__114)
    local t_32;
    if (index__114 < temper.string_end(str__113)) then
      t_32 = (this__18.char__111 == temper.string_get(str__113, index__114));
    else
      t_32 = false;
    end
    if t_32 then
      return temper.string_next(str__113, index__114);
    else
      return -1.0;
    end
  end;
  SingleMatcher.methods.toString = function(this__19)
    return chr(this__19.char__111);
  end;
  SingleMatcher.constructor = function(this__46, char__119)
    this__46.char__111 = char__119;
    return nil;
  end;
  SingleMatcher.get.char = function(this__166)
    return this__166.char__111;
  end;
  ContainsMatcher = temper.type('ContainsMatcher', Matcher);
  ContainsMatcher.methods.matches = function(this__21, str__124, index__125)
    local t_33;
    if (index__125 < temper.string_end(str__124)) then
      t_33 = temper.bool_eq(temper.densebitvector_get(this__21.bitset__120, temper.string_get(str__124, index__125)), this__21.want__122);
    else
      t_33 = false;
    end
    if t_33 then
      return temper.string_next(str__124, index__125);
    else
      return -1.0;
    end
  end;
  ContainsMatcher.methods.toString = function(this__22)
    local inner__129;
    inner__129 = temper.listed_join(this__22.options__121, '', chr);
    if not this__22.want__122 then
      return temper.concat('[^', inner__129, ']');
    else
      return temper.concat('[', inner__129, ']');
    end
  end;
  ContainsMatcher.constructor = function(this__50, bitset__131, options__132, want__133)
    this__50.bitset__120 = bitset__131;
    this__50.options__121 = options__132;
    this__50.want__122 = want__133;
    return nil;
  end;
  ContainsMatcher.get.bitset = function(this__169)
    return this__169.bitset__120;
  end;
  ContainsMatcher.get.options = function(this__172)
    return this__172.options__121;
  end;
  ContainsMatcher.get.want = function(this__175)
    return this__175.want__122;
  end;
  StartGroupMatcher = temper.type('StartGroupMatcher', Matcher);
  StartGroupMatcher.methods.matches = function(this__24, str__136, index__137)
    return index__137;
  end;
  StartGroupMatcher.methods.toString = function(this__25)
    return temper.concat('<', this__25.name__134, '>');
  end;
  StartGroupMatcher.constructor = function(this__55, name__142)
    this__55.name__134 = name__142;
    return nil;
  end;
  StartGroupMatcher.get.name = function(this__178)
    return this__178.name__134;
  end;
  EndGroupMatcher = temper.type('EndGroupMatcher', Matcher);
  EndGroupMatcher.methods.matches = function(this__26, str__145, index__146)
    return index__146;
  end;
  EndGroupMatcher.methods.toString = function(this__27)
    return temper.concat('</', this__27.name__143, '>');
  end;
  EndGroupMatcher.constructor = function(this__59, name__151)
    this__59.name__143 = name__151;
    return nil;
  end;
  EndGroupMatcher.get.name = function(this__181)
    return this__181.name__143;
  end;
  EpsilonMatcher = temper.type('EpsilonMatcher', Matcher);
  EpsilonMatcher.methods.matches = function(this__28, str__153, index__154)
    return index__154;
  end;
  EpsilonMatcher.methods.toString = function(this__29)
    return '\xce\xb5';
  end;
  EpsilonMatcher.constructor = function(this__63)
    return nil;
  end;
  noMatch = temper.listof();
  fromChars = function(options__106, want__107)
    local return__45, t_34, bitset__109, i__110;
    ::continue_1::
    if (temper.listed_length(options__106) == 1) then
      t_34 = want__107;
    else
      t_34 = false;
    end
    if t_34 then
      return__45 = SingleMatcher(temper.listed_get(options__106, 0));
      goto break_0;
    end
    bitset__109 = temper.densebitvector_constructor(0);
    i__110 = 0;
    while (i__110 < temper.listed_length(options__106)) do
      temper.densebitvector_set(bitset__109, temper.listed_get(options__106, i__110), true);
      i__110 = temper.int32_add(i__110, 1);
    end
    do
      return ContainsMatcher(bitset__109, options__106, want__107);
    end
    ::break_0::return return__45;
  end;
  charCodeMatcher = function(c__77)
    return fromChars(temper.listof(c__77), true);
  end;
  exports = {};
  exports.Matcher = Matcher;
  exports.BeginMatcher = BeginMatcher;
  exports.EndMatcher = EndMatcher;
  exports.WordBoundaryMatcher = WordBoundaryMatcher;
  exports.SingleMatcher = SingleMatcher;
  exports.ContainsMatcher = ContainsMatcher;
  exports.StartGroupMatcher = StartGroupMatcher;
  exports.EndGroupMatcher = EndGroupMatcher;
  exports.EpsilonMatcher = EpsilonMatcher;
  exports.fromChars = fromChars;
  exports.charCodeMatcher = charCodeMatcher;
  return exports;
end
package.preload['temper-regex-engine/nfa/nfa'] = function()
  local temper = require('temper-core');
  local entire, oneOrMore, optional, Begin, Dot, End, WordBoundary, Digit, Space, Word, charCodeMatcher, fromChars, regexToString, bogusMatch, chr, isWordChar, wordChars, spaceChars, wordCharsList, spaceCharsList, digitCharsList, DFA, TemperRegex, Engine, Lua, Javascript, CodePoints, CodeSet, SpecialSet, CodeRange, Or, Repeat, Sequence, BeginSpecial, BeginMatcher, EndSpecial, EndMatcher, WordBoundarySpecial, WordBoundaryMatcher, DotSpecial, Capture, StartGroupMatcher, EndGroupMatcher, SpaceSpecial, DigitSpecial, withScan, TemperRegexNFA, setChars, exports;
  entire = temper.import('std/regex', 'entire');
  oneOrMore = temper.import('std/regex', 'oneOrMore');
  optional = temper.import('std/regex', 'optional');
  Begin = temper.import('std/regex', 'Begin');
  Dot = temper.import('std/regex', 'Dot');
  End = temper.import('std/regex', 'End');
  WordBoundary = temper.import('std/regex', 'WordBoundary');
  Digit = temper.import('std/regex', 'Digit');
  Space = temper.import('std/regex', 'Space');
  Word = temper.import('std/regex', 'Word');
  charCodeMatcher = temper.import('temper-regex-engine/nfa/matcher', 'charCodeMatcher');
  fromChars = temper.import('temper-regex-engine/nfa/matcher', 'fromChars');
  regexToString = temper.import('temper-regex-engine/util', 'regexToString');
  bogusMatch = temper.import('temper-regex-engine/util', 'bogusMatch');
  chr = temper.import('temper-regex-engine/util', 'chr');
  isWordChar = temper.import('temper-regex-engine/ctype', 'isWordChar');
  wordChars = temper.import('temper-regex-engine/ctype', 'wordChars');
  spaceChars = temper.import('temper-regex-engine/ctype', 'spaceChars');
  wordCharsList = temper.import('temper-regex-engine/ctype', 'wordCharsList');
  spaceCharsList = temper.import('temper-regex-engine/ctype', 'spaceCharsList');
  digitCharsList = temper.import('temper-regex-engine/ctype', 'digitCharsList');
  DFA = temper.import('temper-regex-engine/dfa/dfa', 'DFA');
  TemperRegex = temper.import('temper-regex-engine/regex', 'TemperRegex');
  Engine = temper.import('temper-regex-engine/nfa/engine', 'Engine');
  Lua = temper.import('temper-regex-engine/dfa/lua', 'Lua');
  Javascript = temper.import('temper-regex-engine/dfa/javascript', 'Javascript');
  CodePoints = temper.import('std/regex', 'CodePoints');
  CodeSet = temper.import('std/regex', 'CodeSet');
  SpecialSet = temper.import('std/regex', 'SpecialSet');
  CodeRange = temper.import('std/regex', 'CodeRange');
  Or = temper.import('std/regex', 'Or');
  Repeat = temper.import('std/regex', 'Repeat');
  Sequence = temper.import('std/regex', 'Sequence');
  BeginSpecial = temper.import('std/regex', 'BeginSpecial');
  BeginMatcher = temper.import('temper-regex-engine/nfa/matcher', 'BeginMatcher');
  EndSpecial = temper.import('std/regex', 'EndSpecial');
  EndMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EndMatcher');
  WordBoundarySpecial = temper.import('std/regex', 'WordBoundarySpecial');
  WordBoundaryMatcher = temper.import('temper-regex-engine/nfa/matcher', 'WordBoundaryMatcher');
  DotSpecial = temper.import('std/regex', 'DotSpecial');
  Capture = temper.import('std/regex', 'Capture');
  StartGroupMatcher = temper.import('temper-regex-engine/nfa/matcher', 'StartGroupMatcher');
  EndGroupMatcher = temper.import('temper-regex-engine/nfa/matcher', 'EndGroupMatcher');
  SpaceSpecial = temper.import('std/regex', 'SpaceSpecial');
  DigitSpecial = temper.import('std/regex', 'DigitSpecial');
  withScan = function(regex__97, makeScan__98)
    local nfa__100, dfa__101;
    nfa__100 = TemperRegexNFA.fromRegex(regex__97);
    dfa__101 = DFA.fromEngine(nfa__100.engine);
    if not temper.is_null(dfa__101) then
      local dfa_112, scan__102, t_113;
      dfa_112 = dfa__101;
      scan__102 = makeScan__98(dfa_112.table);
      t_113 = function(str__103, from__104)
        return scan__102(str__103, from__104);
      end;
      (nfa__100.engine)['scan'] = t_113;
    end
    return nfa__100;
  end;
  TemperRegexNFA = temper.type('TemperRegexNFA', TemperRegex);
  TemperRegexNFA.fromRegex = function(regex__110)
    local nfa__112;
    nfa__112 = TemperRegexNFA(Engine());
    nfa__112:buildRegex(regex__110, nfa__112.engine.startState, nfa__112.engine.endState);
    return nfa__112;
  end;
  TemperRegexNFA.fromRegexDFA = function(regex__114)
    local fn__266;
    fn__266 = function(table__116)
      local fn_114;
      fn_114 = function(str__117, from__118)
        return table__116:scan(str__117, from__118);
      end;
      return fn_114;
    end;
    return withScan(regex__114, fn__266);
  end;
  TemperRegexNFA.fromRegexLua = function(regex__121, load__122)
    local fn__265;
    fn__265 = function(table__124)
      return load__122(Lua.fromTable(table__124));
    end;
    return withScan(regex__121, fn__265);
  end;
  TemperRegexNFA.fromRegexJavascript = function(regex__126, load__127)
    local fn__264;
    fn__264 = function(table__129)
      return load__127(Javascript.fromTable(table__129));
    end;
    return withScan(regex__126, fn__264);
  end;
  TemperRegexNFA.methods.buildRegex = function(this__13, regex__131, input__132, output__133)
    local t_115, lastState__137, index__138, t_116, ls__141, j__142, t_120, i__147, t_121, lastState__148, i__149, max__151, t_123, items__155, t_124;
    if temper.instance_of(regex__131, Capture) then
      local t_125, mid1__135, mid2__136;
      t_125 = temper.cast_to(regex__131, Capture);
      this__13.engine__108:addGroupName(t_125.name);
      mid1__135 = this__13.engine__108:newState();
      mid2__136 = this__13.engine__108:newState();
      input__132:addNext(StartGroupMatcher(t_125.name), mid1__135);
      this__13:buildRegex(t_125.item, mid1__135, mid2__136);
      mid2__136:addNext(EndGroupMatcher(t_125.name), output__133);
      return nil;
    elseif temper.instance_of(regex__131, CodePoints) then
      t_115 = temper.cast_to(regex__131, CodePoints);
      if (#t_115.value == 0.0) then
        input__132:addNext(this__13.engine__108.epsilon, output__133);
      end
      lastState__137 = input__132;
      index__138 = 1.0;
      while temper.string_hasindex(t_115.value, index__138) do
        local chr__139, curState__140;
        chr__139 = temper.string_get(t_115.value, index__138);
        index__138 = temper.string_next(t_115.value, index__138);
        if temper.string_hasindex(t_115.value, index__138) then
          curState__140 = this__13.engine__108:newState();
        else
          curState__140 = output__133;
        end
        lastState__137:addNext(charCodeMatcher(chr__139), curState__140);
        lastState__137 = curState__140;
      end
      return nil;
    elseif temper.instance_of(regex__131, CodeSet) then
      t_116 = temper.cast_to(regex__131, CodeSet);
      ls__141 = temper.listbuilder_constructor();
      j__142 = 0;
      while (j__142 < temper.list_length(t_116.items)) do
        local subRegex__143, t_117, split__145, i__146, t_118;
        subRegex__143 = temper.list_get(t_116.items, j__142);
        if temper.instance_of(subRegex__143, CodeRange) then
          local t_119, i__144;
          t_119 = temper.cast_to(subRegex__143, CodeRange);
          i__144 = t_119.min;
          while (i__144 <= t_119.max) do
            temper.listbuilder_add(ls__141, i__144);
            i__144 = temper.int32_add(i__144, 1);
          end
        elseif temper.instance_of(subRegex__143, CodePoints) then
          t_117 = temper.cast_to(subRegex__143, CodePoints);
          split__145 = temper.string_split(t_117.value, '');
          i__146 = 0;
          while (i__146 < temper.list_length(split__145)) do
            temper.listbuilder_add(ls__141, temper.string_get(temper.list_get(split__145, i__146), 1.0));
            i__146 = temper.int32_add(i__146, 1);
          end
        elseif temper.instance_of(subRegex__143, SpecialSet) then
          t_118 = temper.cast_to(subRegex__143, SpecialSet);
          temper.listbuilder_addall(ls__141, setChars(t_118));
        end
        j__142 = temper.int32_add(j__142, 1);
      end
      input__132:addNext(fromChars(ls__141, not t_116.negated), output__133);
      return nil;
    elseif temper.instance_of(regex__131, Or) then
      t_120 = temper.cast_to(regex__131, Or);
      i__147 = 0;
      while (i__147 < temper.list_length(t_120.items)) do
        this__13:buildRegex(temper.list_get(t_120.items, i__147), input__132, output__133);
        i__147 = temper.int32_add(i__147, 1);
      end
      return nil;
    elseif temper.instance_of(regex__131, Repeat) then
      t_121 = temper.cast_to(regex__131, Repeat);
      lastState__148 = input__132;
      i__149 = 0;
      while (i__149 < t_121.min) do
        local curState__150;
        curState__150 = this__13.engine__108:newState();
        this__13:buildRegex(t_121.item, lastState__148, curState__150);
        lastState__148 = curState__150;
        i__149 = temper.int32_add(i__149, 1);
      end
      max__151 = t_121.max;
      if not temper.is_null(max__151) then
        local max_122, i__152;
        max_122 = max__151;
        i__152 = t_121.min;
        while (i__152 < max_122) do
          local curState__153;
          curState__153 = this__13.engine__108:newState();
          if t_121.reluctant then
            lastState__148:addNext(this__13.engine__108.epsilon, output__133);
            this__13:buildRegex(t_121.item, lastState__148, curState__153);
          else
            this__13:buildRegex(t_121.item, lastState__148, curState__153);
            lastState__148:addNext(this__13.engine__108.epsilon, output__133);
          end
          lastState__148 = curState__153;
          i__152 = temper.int32_add(i__152, 1);
        end
        lastState__148:addNext(this__13.engine__108.epsilon, output__133);
        return nil;
      else
        local loop__154;
        loop__154 = this__13.engine__108:newState();
        lastState__148:addNext(this__13.engine__108.epsilon, loop__154);
        if t_121.reluctant then
          loop__154:addNext(this__13.engine__108.epsilon, output__133);
          this__13:buildRegex(t_121.item, loop__154, loop__154);
          return nil;
        else
          this__13:buildRegex(t_121.item, loop__154, loop__154);
          loop__154:addNext(this__13.engine__108.epsilon, output__133);
          return nil;
        end
      end
    elseif temper.instance_of(regex__131, Sequence) then
      t_123 = temper.cast_to(regex__131, Sequence);
      items__155 = t_123.items;
      if temper.listed_isempty(items__155) then
        input__132:addNext(this__13.engine__108.epsilon, output__133);
        return nil;
      else
        local v1__156, i__157;
        v1__156 = input__132;
        i__157 = 0;
        while (i__157 < temper.list_length(items__155)) do
          local v2__158;
          if (temper.int32_add(i__157, 1) < temper.list_length(items__155)) then
            v2__158 = this__13.engine__108:newState();
          else
            v2__158 = output__133;
          end
          this__13:buildRegex(temper.list_get(items__155, i__157), v1__156, v2__158);
          v1__156 = v2__158;
          i__157 = temper.int32_add(i__157, 1);
        end
        return nil;
      end
    elseif temper.instance_of(regex__131, BeginSpecial) then
      input__132:addNext(BeginMatcher(), output__133);
      return nil;
    elseif temper.instance_of(regex__131, EndSpecial) then
      input__132:addNext(EndMatcher(), output__133);
      return nil;
    elseif temper.instance_of(regex__131, WordBoundarySpecial) then
      input__132:addNext(WordBoundaryMatcher(), output__133);
      return nil;
    elseif temper.instance_of(regex__131, DotSpecial) then
      input__132:addNext(fromChars(temper.listof(10), false), output__133);
      return nil;
    elseif temper.instance_of(regex__131, SpecialSet) then
      t_124 = temper.cast_to(regex__131, SpecialSet);
      input__132:addNext(fromChars(setChars(t_124), true), output__133);
      return nil;
    else
      return nil;
    end
  end;
  TemperRegexNFA.methods.replace = function(this__14, text__160, format__161)
    return this__14.engine__108:replace(text__160, format__161);
  end;
  TemperRegexNFA.methods.split = function(this__15, text__164)
    return this__15.engine__108:split(text__164);
  end;
  TemperRegexNFA.methods.found = function(this__16, text__167)
    return this__16.engine__108:found(text__167, 1.0);
  end;
  TemperRegexNFA.methods.find = function(this__17, text__170, begin__194)
    local begin__171;
    if temper.is_null(begin__194) then
      begin__171 = 1.0;
    else
      begin__171 = begin__194;
    end
    return this__17.engine__108:find(text__170, begin__171);
  end;
  TemperRegexNFA.constructor = function(this__21, engine__174)
    this__21.engine__108 = engine__174;
    return nil;
  end;
  TemperRegexNFA.get.engine = function(this__180)
    return this__180.engine__108;
  end;
  setChars = function(special__106)
    if temper.instance_of(special__106, DigitSpecial) then
      return digitCharsList;
    elseif temper.instance_of(special__106, SpaceSpecial) then
      return spaceCharsList;
    else
      return wordCharsList;
    end
  end;
  exports = {};
  exports.TemperRegexNFA = TemperRegexNFA;
  return exports;
end
package.preload['temper-regex-engine/nfa/stack'] = function()
  local temper = require('temper-core');
  local Span, StackSlot, exports;
  Span = temper.type('Span');
  Span.constructor = function(this__0, name__14, begin__15, end__16, rest__17)
    if (end__16 == nil) then
      end__16 = temper.null;
    end
    if (rest__17 == nil) then
      rest__17 = temper.null;
    end
    this__0.name__9 = name__14;
    this__0.begin__10 = begin__15;
    this__0.end__11 = end__16;
    this__0.rest__12 = rest__17;
    return nil;
  end;
  Span.get.name = function(this__36)
    return this__36.name__9;
  end;
  Span.get.begin = function(this__39)
    return this__39.begin__10;
  end;
  Span.get.end_ = function(this__42)
    return this__42.end__11;
  end;
  Span.get.rest = function(this__45)
    return this__45.rest__12;
  end;
  StackSlot = temper.type('StackSlot');
  StackSlot.constructor = function(this__4, index__23, pos__24, state__25, spans__26)
    if (spans__26 == nil) then
      spans__26 = temper.null;
    end
    this__4.index__18 = index__23;
    this__4.pos__19 = pos__24;
    this__4.state__20 = state__25;
    this__4.spans__21 = spans__26;
    return nil;
  end;
  StackSlot.get.index = function(this__48)
    return this__48.index__18;
  end;
  StackSlot.get.pos = function(this__51)
    return this__51.pos__19;
  end;
  StackSlot.get.state = function(this__54)
    return this__54.state__20;
  end;
  StackSlot.get.spans = function(this__57)
    return this__57.spans__21;
  end;
  exports = {};
  exports.Span = Span;
  exports.StackSlot = StackSlot;
  return exports;
end
package.preload['temper-regex-engine/nfa/state'] = function()
  local temper = require('temper-core');
  local charCodeMatcher, fromChars, Next, State, exports;
  charCodeMatcher = temper.import('temper-regex-engine/nfa/matcher', 'charCodeMatcher');
  fromChars = temper.import('temper-regex-engine/nfa/matcher', 'fromChars');
  Next = temper.type('Next');
  Next.constructor = function(this__6, toState__28, matcher__29)
    this__6.toState__25 = toState__28;
    this__6.matcher__26 = matcher__29;
    return nil;
  end;
  Next.get.toState = function(this__72)
    return this__72.toState__25;
  end;
  Next.get.matcher = function(this__75)
    return this__75.matcher__26;
  end;
  State = temper.type('State');
  State.get.next = function(this__3)
    return this__3.nextVar__33;
  end;
  State.methods.addNext = function(this__4, matcher__36, toState__37)
    local t_35;
    temper.listbuilder_add(this__4.next, Next(toState__37, matcher__36));
    t_35 = temper.int32_add(toState__37.incoming, 1);
    toState__37['incoming'] = t_35;
    return nil;
  end;
  State.methods.addNextFirst = function(this__5, matcher__40, toState__41)
    local oldNext__43, t_36, t_37, i__44;
    oldNext__43 = this__5.next;
    t_36 = temper.listbuilder_constructor();
    this__5.nextVar__33 = t_36;
    temper.listbuilder_add(this__5.next, Next(toState__41, matcher__40));
    t_37 = temper.int32_add(toState__41.incoming, 1);
    toState__41['incoming'] = t_37;
    i__44 = 0;
    while (i__44 < temper.listbuilder_length(oldNext__43)) do
      temper.listbuilder_add(this__5.next, temper.listed_get(oldNext__43, i__44));
      i__44 = temper.int32_add(i__44, 1);
    end
    return nil;
  end;
  State.constructor = function(this__9, id__46)
    local t_38;
    this__9.id__30 = id__46;
    t_38 = temper.listbuilder_constructor();
    this__9.nextVar__33 = t_38;
    this__9.incoming__34 = 0;
    return nil;
  end;
  State.get.id = function(this__55)
    return this__55.id__30;
  end;
  State.get.nextVar = function(this__58)
    return this__58.nextVar__33;
  end;
  State.set.nextVar = function(this__62, newNextVar__61)
    this__62.nextVar__33 = newNextVar__61;
    return nil;
  end;
  State.get.incoming = function(this__65)
    return this__65.incoming__34;
  end;
  State.set.incoming = function(this__69, newIncoming__68)
    this__69.incoming__34 = newIncoming__68;
    return nil;
  end;
  exports = {};
  exports.Next = Next;
  exports.State = State;
  return exports;
end
package.preload['temper-regex-engine/regex'] = function()
  local temper = require('temper-core');
  local entire, oneOrMore, optional, Begin, Dot, End, WordBoundary, Digit, Space, Word, TemperRegex, exports;
  entire = temper.import('std/regex', 'entire');
  oneOrMore = temper.import('std/regex', 'oneOrMore');
  optional = temper.import('std/regex', 'optional');
  Begin = temper.import('std/regex', 'Begin');
  Dot = temper.import('std/regex', 'Dot');
  End = temper.import('std/regex', 'End');
  WordBoundary = temper.import('std/regex', 'WordBoundary');
  Digit = temper.import('std/regex', 'Digit');
  Space = temper.import('std/regex', 'Space');
  Word = temper.import('std/regex', 'Word');
  TemperRegex = temper.type('TemperRegex');
  TemperRegex.methods.replace = function(this__0, text__40, format__41)
    temper.virtual();
  end;
  TemperRegex.methods.split = function(this__1, text__44)
    temper.virtual();
  end;
  TemperRegex.methods.found = function(this__2, text__47)
    temper.virtual();
  end;
  TemperRegex.methods.find = function(this__3, text__50, begin__51)
    if (begin__51 == nil) then
      begin__51 = temper.null;
    end
    temper.virtual();
  end;
  exports = {};
  exports.TemperRegex = TemperRegex;
  return exports;
end
package.preload['temper-regex-engine/util'] = function()
  local temper = require('temper-core');
  local entire, oneOrMore, optional, Begin, Dot, End, WordBoundary, Digit, Space, Word, CodePoints, CodeSet, CodeRange, Or, Repeat, Capture, Special, Sequence, BeginSpecial, DotSpecial, EndSpecial, WordBoundarySpecial, DigitSpecial, SpaceSpecial, WordSpecial, Match, Group, regexToString, bogusMatch, chr, exports;
  entire = temper.import('std/regex', 'entire');
  oneOrMore = temper.import('std/regex', 'oneOrMore');
  optional = temper.import('std/regex', 'optional');
  Begin = temper.import('std/regex', 'Begin');
  Dot = temper.import('std/regex', 'Dot');
  End = temper.import('std/regex', 'End');
  WordBoundary = temper.import('std/regex', 'WordBoundary');
  Digit = temper.import('std/regex', 'Digit');
  Space = temper.import('std/regex', 'Space');
  Word = temper.import('std/regex', 'Word');
  CodePoints = temper.import('std/regex', 'CodePoints');
  CodeSet = temper.import('std/regex', 'CodeSet');
  CodeRange = temper.import('std/regex', 'CodeRange');
  Or = temper.import('std/regex', 'Or');
  Repeat = temper.import('std/regex', 'Repeat');
  Capture = temper.import('std/regex', 'Capture');
  Special = temper.import('std/regex', 'Special');
  Sequence = temper.import('std/regex', 'Sequence');
  BeginSpecial = temper.import('std/regex', 'BeginSpecial');
  DotSpecial = temper.import('std/regex', 'DotSpecial');
  EndSpecial = temper.import('std/regex', 'EndSpecial');
  WordBoundarySpecial = temper.import('std/regex', 'WordBoundarySpecial');
  DigitSpecial = temper.import('std/regex', 'DigitSpecial');
  SpaceSpecial = temper.import('std/regex', 'SpaceSpecial');
  WordSpecial = temper.import('std/regex', 'WordSpecial');
  Match = temper.import('std/regex', 'Match');
  Group = temper.import('std/regex', 'Group');
  regexToString = function(base__52)
    local t_0, ret__54, j__55, inv__57, fn__206, t_5, ret__61, add__60, i__65, fn__205, mid__66, t_8, mod__69, t_9, q__70, str__71, item__72, pat__73, t_19, t_20, value__74, t_21, t_22, strings__75, i__76, fn__204;
    if temper.instance_of(base__52, Capture) then
      local t_24, t_25;
      t_24 = temper.cast_to(base__52, Capture);
      t_25 = regexToString(t_24.item);
      return temper.concat('(?', t_24.name, '=', t_25, ')');
    elseif temper.instance_of(base__52, CodePoints) then
      return (temper.cast_to(base__52, CodePoints)).value;
    elseif temper.instance_of(base__52, CodeSet) then
      t_0 = temper.cast_to(base__52, CodeSet);
      ret__54 = temper.listbuilder_constructor();
      j__55 = 0;
      while (j__55 < temper.list_length(t_0.items)) do
        local subBase__56;
        subBase__56 = temper.list_get(t_0.items, j__55);
        if temper.instance_of(subBase__56, CodeRange) then
          local t_1, t_2, t_3;
          t_1 = temper.cast_to(subBase__56, CodeRange);
          t_2 = temper.string_fromcodepoint(t_1.min);
          t_3 = temper.string_fromcodepoint(t_1.max);
          temper.listbuilder_add(ret__54, temper.concat(t_2, '-', t_3));
        else
          local t_4;
          t_4 = regexToString(subBase__56);
          temper.listbuilder_add(ret__54, t_4);
        end
        j__55 = temper.int32_add(j__55, 1);
      end
      if t_0.negated then
        inv__57 = '^';
      else
        inv__57 = '';
      end
      fn__206 = function(s__58)
        return s__58;
      end;
      return temper.concat('[', inv__57, temper.listed_join(ret__54, '', fn__206), ']');
    elseif temper.instance_of(base__52, Or) then
      t_5 = temper.cast_to(base__52, Or);
      ret__61 = temper.mapbuilder_constructor();
      add__60 = function(ent__62)
        if temper.instance_of(ent__62, Or) then
          local t_6, i__64;
          t_6 = temper.cast_to(ent__62, Or);
          i__64 = 0;
          while (i__64 < temper.list_length(t_6.items)) do
            add__60(temper.list_get(t_6.items, i__64));
            i__64 = temper.int32_add(i__64, 1);
          end
        else
          local t_7;
          t_7 = regexToString(ent__62);
          temper.mapbuilder_set(ret__61, t_7, true);
        end
        return nil;
      end;
      i__65 = 0;
      while (i__65 < temper.list_length(t_5.items)) do
        add__60(temper.list_get(t_5.items, i__65));
        i__65 = temper.int32_add(i__65, 1);
      end
      fn__205 = function(p__67)
        return p__67.key;
      end;
      mid__66 = temper.listed_join(temper.mapped_tolist(ret__61), '|', fn__205);
      return temper.concat('(?:', mid__66, ')');
    elseif temper.instance_of(base__52, Repeat) then
      t_8 = temper.cast_to(base__52, Repeat);
      if (t_8.min == 0) then
        local t_10, t_11;
        t_11 = t_8.max;
        if temper.is_null(t_11) then
          t_10 = false;
        else
          t_10 = (t_11 == 1);
        end
        t_9 = t_10;
      else
        t_9 = false;
      end
      if t_9 then
        mod__69 = '?';
      else
        local t_12;
        if (t_8.min == 0) then
          t_12 = temper.is_null(t_8.max);
        else
          t_12 = false;
        end
        if t_12 then
          mod__69 = '*';
        else
          local t_13;
          if (t_8.min == 1) then
            t_13 = temper.is_null(t_8.max);
          else
            t_13 = false;
          end
          if t_13 then
            mod__69 = '+';
          else
            local t_14, t_15, t_16;
            t_15 = t_8.min;
            t_16 = t_8.max;
            if temper.is_null(t_16) then
              t_14 = false;
            else
              t_14 = (t_15 == t_16);
            end
            if t_14 then
              mod__69 = temper.concat('{', temper.int32_tostring(t_8.min), '}');
            elseif temper.is_null(t_8.max) then
              mod__69 = temper.concat('{', temper.int32_tostring(t_8.min), ',}');
            else
              local t_17, t_18;
              t_18 = t_8.max;
              if temper.is_null(t_18) then
                temper.bubble();
              else
                t_17 = t_18;
              end
              mod__69 = temper.concat('{', temper.int32_tostring(t_8.min), ',', temper.int32_tostring(t_17), '}');
            end
          end
        end
      end
      if t_8.reluctant then
        q__70 = '?';
      else
        q__70 = '';
      end
      str__71 = regexToString(t_8.item);
      item__72 = t_8.item;
      if temper.instance_of(item__72, CodeSet) then
        t_19 = true;
      elseif temper.instance_of(item__72, Or) then
        t_19 = true;
      elseif temper.instance_of(item__72, Capture) then
        t_19 = true;
      else
        t_19 = temper.instance_of(item__72, Special);
      end
      if t_19 then
        pat__73 = str__71;
      elseif temper.instance_of(item__72, CodePoints) then
        t_20 = temper.cast_to(item__72, CodePoints);
        value__74 = t_20.value;
        if temper.string_hasindex(value__74, 1.0) then
          t_21 = not temper.string_hasindex(value__74, temper.string_next(value__74, 1.0));
        else
          t_21 = false;
        end
        if t_21 then
          pat__73 = str__71;
        else
          pat__73 = temper.concat('(?:', str__71, ')');
        end
      else
        pat__73 = temper.concat('(?:', str__71, ')');
      end
      return temper.concat(pat__73, mod__69, q__70);
    elseif temper.instance_of(base__52, Sequence) then
      t_22 = temper.cast_to(base__52, Sequence);
      strings__75 = temper.listbuilder_constructor();
      i__76 = 0;
      while (i__76 < temper.list_length(t_22.items)) do
        local t_23;
        t_23 = regexToString(temper.list_get(t_22.items, i__76));
        temper.listbuilder_add(strings__75, t_23);
        i__76 = temper.int32_add(i__76, 1);
      end
      fn__204 = function(x__77)
        return x__77;
      end;
      return temper.listed_join(strings__75, '', fn__204);
    elseif temper.instance_of(base__52, BeginSpecial) then
      return '^';
    elseif temper.instance_of(base__52, DotSpecial) then
      return '.';
    elseif temper.instance_of(base__52, EndSpecial) then
      return '$';
    elseif temper.instance_of(base__52, WordBoundarySpecial) then
      return '\\b';
    elseif temper.instance_of(base__52, DigitSpecial) then
      return '\\d';
    elseif temper.instance_of(base__52, SpaceSpecial) then
      return '\\s';
    elseif temper.instance_of(base__52, WordSpecial) then
      return '\\w';
    else
      temper.bubble();
    end
  end;
  bogusMatch = function()
    local bogusEnd__79;
    bogusEnd__79 = 1.0;
    return Match(Group('', '', bogusEnd__79, bogusEnd__79), temper.map_constructor(temper.listof()));
  end;
  chr = function(i__80)
    local local_26, local_27, local_28;
    local_26, local_27, local_28 = temper.pcall(function()
      return temper.string_fromcodepoint(i__80), 'return';
    end);
    if local_26 then
      if (local_28 == 'return') then
        return local_27;
      end
    else
      return temper.bubble();
    end
  end;
  exports = {};
  exports.regexToString = regexToString;
  exports.bogusMatch = bogusMatch;
  exports.chr = chr;
  return exports;
end
