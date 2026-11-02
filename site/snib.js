// The mark is a snib: a bar turning on a pin, set in a plate. Bar vertical = snibbed, bar
// horizontal = open. Same geometry, rotated 90 degrees — the one idea the whole product rests on.
(function () {
  var BRASS = '#B08A4A';
  var VERDIGRIS = '#3E7C74';

  function markSvg() {
    return (
      '<svg class="snib-svg" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
      '<rect x="6" y="6" width="52" height="52" rx="15" fill="none" stroke="currentColor" stroke-width="4"/>' +
      '<path class="snib-bar" fill="currentColor" fill-rule="evenodd" ' +
      'd="M32,12 A7,7 0 0 1 39,19 V45 A7,7 0 0 1 32,52 A7,7 0 0 1 25,45 V19 A7,7 0 0 1 32,12 Z ' +
      'M35.5,32 A3.5,3.5 0 1 1 28.5,32 A3.5,3.5 0 1 1 35.5,32 Z"/>' +
      '</svg>'
    );
  }

  function fill(id, color) {
    var el = document.getElementById(id);
    if (!el) return null;
    el.innerHTML = markSvg();
    el.style.color = color || BRASS;
    return el.querySelector('.snib-svg');
  }

  // Static marks: header and footer, snibbed, brass.
  fill('headerMark', BRASS);
  fill('footMark', BRASS);

  // The big hero mark toggles between snibbed and open on tap, demonstrating the core idea.
  var big = document.getElementById('bigMark');
  var caption = document.getElementById('markCaption');
  if (big) {
    var svg = fill('bigMark', BRASS);
    var snibbed = true;
    big.addEventListener('click', function () {
      snibbed = !snibbed;
      svg.classList.toggle('open', !snibbed);
      svg.style.color = snibbed ? BRASS : VERDIGRIS;
      big.setAttribute('aria-pressed', String(snibbed));
      if (caption) caption.textContent = snibbed ? 'Snibbed — tap to open' : 'Open — tap to snib';
    });
  }
})();
