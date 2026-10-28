const assert=require('node:assert/strict');const {eligible,Queue}=require('../launcher/app/src/main/assets/phone/scene-policy.js');
const videos=[{id:'a',category:'space'},{id:'b',category:'cities'},{id:'c',category:'space'}];
assert.deepEqual(eligible(videos,{favorites:['a','b'],excluded:['a'],excludedCategories:[]},{scene_favorites_only:true}).map(x=>x.id),['b']);
assert.deepEqual(eligible(videos,{favorites:['a'],excluded:[],excludedCategories:['space']},{scene_favorites_only:true}),[]);
const queue=new Queue(()=>.35);queue.configure(['a','b','c'],true);const first=queue.step(),second=queue.step(),third=queue.step();assert.equal(new Set([first,second,third]).size,3);assert.notEqual(queue.step(),third);const current=queue.current();queue.step(-1);assert.equal(queue.step(),current);
queue.configure(['a'],true);assert.equal(queue.step(),'a');assert.equal(queue.step(),'a');assert.equal(queue.select('b'),false);queue.configure([],true);assert.equal(queue.step(),null);
const selected=new Queue(()=>.3);selected.configure(['a','b','c'],true);selected.select('a');assert.equal(new Set(['a',selected.step(),selected.step()]).size,3);
console.log('Scene eligibility, shuffle cycles, selection and history checks passed');
