local temper = require('temper-core');
local Powersorter, Runny__34, Run__39, RunPower__40, fill__114, log2__116, floorLog2__115, strictlyDecreasingPrefixEnd__112, reverse__108, weaklyIncreasingPrefixEnd__111, extendAndReverseRunEnd__104, nodePowerDiv__113, copy__103, mergeRunsBasic__106, insertionSort__98, powersort, exports;
Powersorter = temper.type('Powersorter');
Powersorter.methods.sort = function(this__31, items__293)
  local return__74, t_0, t_1, t_2, t_3;
  ::continue_1::
  if (temper.listbuilder_length(items__293) < 2.0) then
    return__74 = nil;
    goto break_0;
  else
  end
  t_1 = this__31.buffer__316;
  t_3 = (temper.listbuilder_length(items__293) + 2.0);
  t_2 = temper.listed_get(items__293, 0.0);
  fill__114(t_1, t_3, t_2);
  t_0 = temper.listbuilder_length(items__293);
  this__31:powerSortPaper(items__293, 0.0, t_0);
  return__74 = nil;
  ::break_0::return return__74;
end;
Powersorter.methods.powerSortPaper = function(this__32, items__296, begin__297, end__298)
  local t_4, t_5, t_6, t_7, t_8, t_9, t_10, t_11, t_12, length__300, maxStackHeight__301, beginStack__302, powerStack__303, top__304, t_13, runA__305, runB__306;
  length__300 = (end__298 - begin__297);
  maxStackHeight__301 = (floorLog2__115(length__300) + 1.0);
  beginStack__302 = temper.listbuilder_constructor();
  powerStack__303 = temper.listbuilder_constructor();
  fill__114(beginStack__302, maxStackHeight__301, 0.0);
  fill__114(powerStack__303, maxStackHeight__301, 0.0);
  top__304 = 0.0;
  t_13 = extendAndReverseRunEnd__104(items__296, begin__297, end__298, this__32.compare__290);
  runA__305 = RunPower__40(begin__297, t_13, 0.0);
  this__32:extendToMinRunLength(items__296, end__298, runA__305);
  runB__306 = Run__39();
  while true do
    if not (runA__305.end_ < end__298) then
      break;
    else
    end
    runB__306['begin'] = runA__305.end_;
    t_4 = runA__305.end_;
    runB__306['end_'] = extendAndReverseRunEnd__104(items__296, t_4, end__298, this__32.compare__290);
    this__32:extendToMinRunLength(items__296, end__298, runB__306);
    runA__305['power'] = nodePowerDiv__113(0.0, length__300, (runA__305.begin - begin__297), (runB__306.begin - begin__297), (runB__306.end_ - begin__297));
    while true do
      local topBegin__307;
      t_10 = temper.listed_get(powerStack__303, top__304);
      if not (t_10 > runA__305.power) then
        break;
      else
      end
      t_11 = temper.listed_get(beginStack__302, top__304);
      topBegin__307 = t_11;
      top__304 = (top__304 - 1.0);
      t_5 = runA__305.begin;
      t_6 = runA__305.end_;
      mergeRunsBasic__106(items__296, topBegin__307, t_5, t_6, this__32.buffer__316, this__32.compare__290);
      runA__305['begin'] = topBegin__307;
    end
    top__304 = (top__304 + 1.0);
    t_7 = runA__305.begin;
    temper.listbuilder_set(beginStack__302, top__304, t_7);
    t_8 = runA__305.power;
    temper.listbuilder_set(powerStack__303, top__304, t_8);
    runA__305['begin'] = runB__306.begin;
    runA__305['end_'] = runB__306.end_;
    runA__305['power'] = 0.0;
  end
  while (top__304 > 0.0) do
    local topBegin__308;
    t_12 = temper.listed_get(beginStack__302, top__304);
    topBegin__308 = t_12;
    top__304 = (top__304 - 1.0);
    t_9 = runA__305.begin;
    mergeRunsBasic__106(items__296, topBegin__308, t_9, end__298, this__32.buffer__316, this__32.compare__290);
    runA__305['begin'] = topBegin__308;
  end
  return nil;
end;
Powersorter.methods.extendToMinRunLength = function(this__33, items__310, end__311, run__312)
  local t_14, t_15, t_16, t_17, length__314;
  length__314 = run__312.length;
  if (length__314 < this__33.minRunLength__291) then
    local beginUnsorted__315;
    t_14 = run__312.begin;
    t_15 = temper.int32_min(end__311, (t_14 + this__33.minRunLength__291));
    run__312['end_'] = t_15;
    beginUnsorted__315 = (run__312.begin + length__314);
    t_16 = run__312.begin;
    t_17 = run__312.end_;
    insertionSort__98(items__310, t_16, t_17, beginUnsorted__315, this__33.compare__290);
  else
  end
  return nil;
end;
Powersorter.constructor = function(this__70, compare__318, minRunLength__319, buffer__320)
  local t_18;
  if not (minRunLength__319 ~= nil) then
    minRunLength__319 = 24.0;
  else
  end
  if not (buffer__320 ~= nil) then
    t_18 = temper.listbuilder_constructor();
    buffer__320 = t_18;
  else
  end
  this__70.compare__290 = compare__318;
  this__70.minRunLength__291 = minRunLength__319;
  this__70.buffer__316 = buffer__320;
  return nil;
end;
Powersorter.get.compare = function(this__450)
  return this__450.compare__290;
end;
Powersorter.get.minRunLength = function(this__454)
  return this__454.minRunLength__291;
end;
Runny__34 = temper.type('Runny__34');
Runny__34.get.begin = function(this__35)
  return__81 = temper.virtual();
end;
Runny__34.get.end_ = function(this__36)
  return__82 = temper.virtual();
end;
Runny__34.set.end_ = function(this__37, value__338)
  return__83 = temper.virtual();
end;
Runny__34.get.length = function(this__38)
  return ((this__38.end_ - this__38.begin) + 0.0);
end;
Run__39 = temper.type('Run__39', Runny__34);
Run__39.constructor = function(this__85, begin__345, end__346)
  if not (begin__345 ~= nil) then
    begin__345 = 0.0;
  else
  end
  if not (end__346 ~= nil) then
    end__346 = 0.0;
  else
  end
  this__85.begin__342 = begin__345;
  this__85.end__343 = end__346;
  return nil;
end;
Run__39.get.begin = function(this__434)
  return this__434.begin__342;
end;
Run__39.set.begin = function(this__438, newBegin__437)
  this__438.begin__342 = newBegin__437;
  return newBegin__437;
end;
Run__39.get.end_ = function(this__442)
  return this__442.end__343;
end;
Run__39.set.end_ = function(this__446, newEnd__445)
  this__446.end__343 = newEnd__445;
  return newEnd__445;
end;
RunPower__40 = temper.type('RunPower__40', Runny__34);
RunPower__40.constructor = function(this__87, begin__351, end__352, power__353)
  if not (begin__351 ~= nil) then
    begin__351 = 0.0;
  else
  end
  if not (end__352 ~= nil) then
    end__352 = 0.0;
  else
  end
  if not (power__353 ~= nil) then
    power__353 = 0.0;
  else
  end
  this__87.begin__347 = begin__351;
  this__87.end__348 = end__352;
  this__87.power__349 = power__353;
  return nil;
end;
RunPower__40.get.begin = function(this__410)
  return this__410.begin__347;
end;
RunPower__40.set.begin = function(this__414, newBegin__413)
  this__414.begin__347 = newBegin__413;
  return newBegin__413;
end;
RunPower__40.get.end_ = function(this__418)
  return this__418.end__348;
end;
RunPower__40.set.end_ = function(this__422, newEnd__421)
  this__422.end__348 = newEnd__421;
  return newEnd__421;
end;
RunPower__40.get.power = function(this__426)
  return this__426.power__349;
end;
RunPower__40.set.power = function(this__430, newPower__429)
  this__430.power__349 = newPower__429;
  return newPower__429;
end;
fill__114 = function(list__359, minLength__360, item__361)
  while true do
    if not (temper.listbuilder_length(list__359) < minLength__360) then
      break;
    else
    end
    temper.listbuilder_add(list__359, item__361);
  end
  return nil;
end;
log2__116 = temper.float64_log(2.0);
floorLog2__115 = function(n__363)
  local t_20, t_21;
  t_20 = temper.int32_tofloat64(n__363);
  t_21 = temper.fdiv(temper.float64_log(t_20), log2__116);
  return temper.float64_toint32(temper.float64_floor(t_21));
end;
strictlyDecreasingPrefixEnd__112 = function(items__282, begin__283, end__284, compare__285)
  local t_22, t_23, t_24, t_25;
  while true do
    if ((begin__283 + 1.0) < end__284) then
      t_23 = temper.listed_get(items__282, begin__283);
      t_24 = temper.listed_get(items__282, (begin__283 + 1.0));
      t_22 = compare__285(t_23, t_24);
      t_25 = (t_22 > 0.0);
    else
      t_25 = false;
    end
    if not t_25 then
      break;
    else
    end
    begin__283 = (begin__283 + 1.0);
  end
  return (begin__283 + 1.0);
end;
reverse__108 = function(items__260, begin__261, end__262)
  local t_26, t_27, t_28, mid__264, i__265;
  mid__264 = temper.int32_div((end__262 - begin__261), 2.0);
  i__265 = 0.0;
  while (i__265 < mid__264) do
    local temp__266;
    t_26 = temper.listed_get(items__260, (begin__261 + i__265));
    temp__266 = t_26;
    t_28 = (begin__261 + i__265);
    t_27 = temper.listed_get(items__260, ((end__262 - i__265) - 1.0));
    temper.listbuilder_set(items__260, t_28, t_27);
    temper.listbuilder_set(items__260, ((end__262 - i__265) - 1.0), temp__266);
    i__265 = (i__265 + 1.0);
  end
  return nil;
end;
weaklyIncreasingPrefixEnd__111 = function(items__277, begin__278, end__279, compare__280)
  local t_29, t_30, t_31, t_32;
  while true do
    if ((begin__278 + 1.0) < end__279) then
      t_30 = temper.listed_get(items__277, begin__278);
      t_31 = temper.listed_get(items__277, (begin__278 + 1.0));
      t_29 = compare__280(t_30, t_31);
      t_32 = (t_29 <= 0.0);
    else
      t_32 = false;
    end
    if not t_32 then
      break;
    else
    end
    begin__278 = (begin__278 + 1.0);
  end
  return (begin__278 + 1.0);
end;
extendAndReverseRunEnd__104 = function(items__194, begin__195, end__196, compare__197)
  local return__58, t_33, t_34, j__199;
  ::continue_3::j__199 = begin__195;
  if (j__199 == end__196) then
    return__58 = j__199;
    goto break_2;
  else
  end
  if ((j__199 + 1.0) == end__196) then
    return__58 = (j__199 + 1.0);
    goto break_2;
  else
  end
  t_33 = temper.listed_get(items__194, j__199);
  t_34 = temper.listed_get(items__194, (j__199 + 1.0));
  if (compare__197(t_33, t_34) > 0.0) then
    local prefixEnd__200;
    prefixEnd__200 = strictlyDecreasingPrefixEnd__112(items__194, begin__195, end__196, compare__197);
    reverse__108(items__194, begin__195, prefixEnd__200);
    return__58 = prefixEnd__200;
  else
    return__58 = weaklyIncreasingPrefixEnd__111(items__194, begin__195, end__196, compare__197);
  end
  ::break_2::return return__58;
end;
nodePowerDiv__113 = function(begin__321, end__322, beginA__323, beginB__324, endB__325)
  local t_35, t_36, t_37, twoN__327, n1__328, n2__329, a__330, b__331, k__332;
  twoN__327 = (2.0 * (end__322 - begin__321));
  n1__328 = (beginB__324 - beginA__323);
  n2__329 = (endB__325 - beginB__324);
  a__330 = (((2.0 * beginA__323) + n1__328) - (2.0 * begin__321));
  b__331 = (((2.0 * beginB__324) + n2__329) - (2.0 * begin__321));
  k__332 = 0.0;
  while true do
    if ((b__331 - a__330) <= twoN__327) then
      t_35 = temper.int32_div(a__330, twoN__327);
      t_36 = temper.int32_div(b__331, twoN__327);
      t_37 = (t_35 == t_36);
    else
      t_37 = false;
    end
    if not t_37 then
      break;
    else
    end
    a__330 = (a__330 * 2.0);
    b__331 = (b__331 * 2.0);
    k__332 = (k__332 + 1.0);
  end
  return k__332;
end;
copy__103 = function(from__178, begin__179, end__180, to__181)
  local t_38, t_39, i__183;
  i__183 = begin__179;
  while (i__183 < end__180) do
    t_39 = (i__183 - begin__179);
    t_38 = temper.listed_get(from__178, i__183);
    temper.listbuilder_set(to__181, t_39, t_38);
    i__183 = (i__183 + 1.0);
  end
  return nil;
end;
mergeRunsBasic__106 = function(items__230, begin__231, middle__232, end__233, buffer__234, compare__235)
  local t_40, t_41, t_42, t_43, t_44, t_45, t_46, t_47, i1__237, end1__238, i2__239, end2__240, out__241;
  copy__103(items__230, begin__231, end__233, buffer__234);
  i1__237 = 0.0;
  end1__238 = (middle__232 - begin__231);
  i2__239 = end1__238;
  end2__240 = (end__233 - begin__231);
  out__241 = begin__231;
  while true do
    local postfixReturn_48;
    if (i1__237 < end1__238) then
      t_40 = (i2__239 < end2__240);
    else
      t_40 = false;
    end
    if not t_40 then
      break;
    else
    end
    postfixReturn_48 = out__241;
    out__241 = (out__241 + 1.0);
    t_41 = temper.listed_get(buffer__234, i1__237);
    t_42 = temper.listed_get(buffer__234, i2__239);
    if (compare__235(t_41, t_42) <= 0.0) then
      local postfixReturn_49;
      postfixReturn_49 = i1__237;
      i1__237 = (i1__237 + 1.0);
      t_43 = temper.listed_get(buffer__234, postfixReturn_49);
      t_45 = t_43;
    else
      local postfixReturn_50;
      postfixReturn_50 = i2__239;
      i2__239 = (i2__239 + 1.0);
      t_44 = temper.listed_get(buffer__234, postfixReturn_50);
      t_45 = t_44;
    end
    temper.listbuilder_set(items__230, postfixReturn_48, t_45);
  end
  while (i1__237 < end1__238) do
    local postfixReturn_51, postfixReturn_52;
    postfixReturn_51 = out__241;
    out__241 = (out__241 + 1.0);
    postfixReturn_52 = i1__237;
    i1__237 = (i1__237 + 1.0);
    t_46 = temper.listed_get(buffer__234, postfixReturn_52);
    temper.listbuilder_set(items__230, postfixReturn_51, t_46);
  end
  while (i2__239 < end2__240) do
    local postfixReturn_53, postfixReturn_54;
    postfixReturn_53 = out__241;
    out__241 = (out__241 + 1.0);
    postfixReturn_54 = i2__239;
    i2__239 = (i2__239 + 1.0);
    t_47 = temper.listed_get(buffer__234, postfixReturn_54);
    temper.listbuilder_set(items__230, postfixReturn_53, t_47);
  end
  return nil;
end;
insertionSort__98 = function(items__143, begin__144, end__145, beginUnsorted__146, compare__147)
  local t_55, t_56, t_57, t_58, t_59, i__149;
  i__149 = beginUnsorted__146;
  while (i__149 < end__145) do
    local j__150, v__151;
    j__150 = i__149;
    t_56 = temper.listed_get(items__143, i__149);
    v__151 = t_56;
    while true do
      if (j__150 > begin__144) then
        t_57 = temper.listed_get(items__143, (j__150 - 1.0));
        t_55 = compare__147(v__151, t_57);
        t_58 = (t_55 < 0.0);
      else
        t_58 = false;
      end
      if not t_58 then
        break;
      else
      end
      t_59 = temper.listed_get(items__143, (j__150 - 1.0));
      temper.listbuilder_set(items__143, j__150, t_59);
      j__150 = (j__150 - 1.0);
    end
    temper.listbuilder_set(items__143, j__150, v__151);
    i__149 = (i__149 + 1.0);
  end
  return nil;
end;
powersort = function(items__287, compare__288)
  Powersorter(compare__288):sort(items__287);
  return nil;
end;
exports = {};
exports.Powersorter = Powersorter;
exports.powersort = powersort;
exports.insertionSort__98 = insertionSort__98;
exports.fill__114 = fill__114;
exports.copy__103 = copy__103;
exports.extendAndReverseRunEnd__104 = extendAndReverseRunEnd__104;
exports.mergeRunsBasic__106 = mergeRunsBasic__106;
exports.reverse__108 = reverse__108;
exports.floorLog2__115 = floorLog2__115;
return exports;
