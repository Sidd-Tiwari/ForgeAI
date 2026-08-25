document.getElementById('refresh').addEventListener('click',e=>{e.currentTarget.textContent='Updated';setTimeout(()=>e.currentTarget.textContent='Refresh data',1200)});
