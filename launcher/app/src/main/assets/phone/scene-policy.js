(function(root){
  'use strict';
  function eligible(videos,preferences,settings){const favorites=new Set(preferences.favorites||[]),excluded=new Set(preferences.excluded||[]),categories=new Set(preferences.excludedCategories||[]);return videos.filter(scene=>!excluded.has(scene.id)&&!categories.has(scene.category)&&(!settings.scene_favorites_only||favorites.has(scene.id)));}
  class Queue{
    constructor(random=Math.random){this.random=random;this.ids=[];this.bag=[];this.history=[];this.cursor=-1;this.index=0;this.shuffle=true;}
    configure(ids,shuffle){ids=[...new Set(ids)];if(JSON.stringify(ids)===JSON.stringify(this.ids)&&shuffle===this.shuffle)return;const current=this.current();this.ids=ids;this.shuffle=shuffle;this.bag=[];this.history=[];this.cursor=-1;this.index=0;if(ids.includes(current))this.select(current);}
    current(){return this.cursor<0?null:this.history[this.cursor];}
    fill(){this.bag=[...this.ids];for(let i=this.bag.length-1;i>0;i--){const j=Math.floor(this.random()*(i+1));[this.bag[i],this.bag[j]]=[this.bag[j],this.bag[i]];}if(this.bag.length>1&&this.bag[0]===this.current())[this.bag[0],this.bag[1]]=[this.bag[1],this.bag[0]];}
    append(id){if(id===this.current())return;this.history.splice(this.cursor+1);this.history.push(id);this.cursor++;if(this.history.length>256){this.history.shift();this.cursor--;}}
    select(id){if(!this.ids.includes(id))return false;this.append(id);if(this.shuffle&&!this.bag.length)this.fill();this.bag=this.bag.filter(value=>value!==id);this.index=(this.ids.indexOf(id)+1)%this.ids.length;return true;}
    step(direction=1){if(!this.ids.length)return null;if(direction<0)return this.history[this.cursor=Math.max(0,this.cursor-1)]||null;if(this.cursor+1<this.history.length)return this.history[++this.cursor];let id;if(this.shuffle){if(!this.bag.length)this.fill();id=this.bag.shift();}else{id=this.ids[this.index];this.index=(this.index+1)%this.ids.length;}this.append(id);return id;}
  }
  const api={eligible,Queue};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.CinemaScenes=api;
})(typeof globalThis!=='undefined'?globalThis:this);
