const {chromium}=require('playwright');
const fs=require('fs');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:"msedge"});
 const page=await browser.newPage({viewport:{width:430,height:900}});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('file:///'+process.cwd().replaceAll('\\','/')+'/shared/song-view/index.html');
 const content=fs.readFileSync('docs/pruebas-melodia/Misa Guelaguetza - Entrada.cho','utf8');
 await page.evaluate(content=>SongView.load({content}),content);
 await page.evaluate(()=>document.fonts.ready);
 console.log(await page.evaluate(()=>({title:document.querySelector('#title').textContent,notes:document.querySelectorAll('.melody-note').length,font:document.fonts.check('20px SongMono'),color:getComputedStyle(document.querySelector('.melody-lyric')).color,background:getComputedStyle(document.querySelector('#sheet')).backgroundColor})));
 await page.screenshot({path:'docs/pruebas-melodia/vista-compartida-clara.png',fullPage:true});
 await page.selectOption('#theme','dark');
 console.log(await page.evaluate(()=>({darkColor:getComputedStyle(document.querySelector('.melody-lyric')).color})));
 await page.selectOption('#mode','separate');
 if(!await page.locator('#separate').isVisible())throw Error('Separate melody missing');
 if(errors.length)throw Error(errors.join('\n'));
 await browser.close();
})();

