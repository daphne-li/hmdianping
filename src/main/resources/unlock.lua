---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by lenovo.
--- DateTime: 2023/6/26 13:50
---
-- 比较线程表示与所锁中的标识是否一致
if(redis.call('get',keys[1])==argv[1])then
-- 释放锁 del key
return redis.call('del',KEYS[1])
end
return 0
