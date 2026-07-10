// Shared behavior for skill-level "battery" pickers and basket-add buttons.
// Used by skills-home.html (dynamically-rendered search results) and
// skill-group.html (server-rendered forms) via document-level delegation.

document.addEventListener('click', function (e) {
  var segment = e.target.closest('.hb-battery-segment');
  if (!segment) {
    return;
  }
  var battery = segment.closest('.hb-battery');
  var val = segment.getAttribute('data-level');
  battery.setAttribute('data-level', val);
  var form = battery.closest('.hb-skill-form');
  if (form) {
    var input = form.querySelector('.hb-level-select');
    if (input) {
      input.value = val;
    }
  }
});

document.addEventListener('mouseover', function (e) {
  var segment = e.target.closest('.hb-battery-segment');
  if (!segment) {
    return;
  }
  var battery = segment.closest('.hb-battery');
  var index = parseInt(segment.getAttribute('data-index'), 10);
  battery.setAttribute('data-hover-level', index);
});

document.addEventListener('mouseout', function (e) {
  var battery = e.target.closest('.hb-battery');
  if (!battery) {
    return;
  }
  var related = e.relatedTarget;
  if (related && battery.contains(related)) {
    return;
  }
  battery.removeAttribute('data-hover-level');
});

var _basketSkillIds = new Set();

function incrementBasketBadge() {
  var link = document.querySelector('.hb-nav-icon-link--basket');
  if (!link) {
    return;
  }
  var badge = link.querySelector('.hb-basket-badge');
  if (badge) {
    var current = parseInt(badge.textContent, 10) || 0;
    badge.textContent = String(current + 1);
  } else {
    badge = document.createElement('span');
    badge.className = 'hb-basket-badge';
    badge.textContent = '1';
    link.appendChild(badge);
  }
}

document.addEventListener('submit', function (e) {
  var form = e.target;
  if (!form.matches('.hb-skill-form')) {
    return;
  }
  e.preventDefault();
  var skillId = form.getAttribute('data-skill-id') || form.querySelector(
      '[name="skillId"]').value;
  var level = form.querySelector('.hb-level-select').value;
  var btn = form.querySelector('button');
  btn.disabled = true;
  fetch('/basket/items', {
    method: 'POST',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: 'skillId=' + encodeURIComponent(skillId) + '&level='
        + encodeURIComponent(level)
  }).then(function (r) {
    if (!r.ok) {
      throw new Error('basket add failed');
    }
    btn.classList.add('hb-btn-basket--added');
    btn.setAttribute('title', 'Added');
    btn.setAttribute('aria-label', 'Added');
    if (!_basketSkillIds.has(skillId)) {
      _basketSkillIds.add(skillId);
      incrementBasketBadge();
    }
  }).catch(function () {
    btn.disabled = false;
    btn.classList.remove('hb-btn-basket--added');
    btn.setAttribute('title', 'Add to basket');
    btn.setAttribute('aria-label', 'Add to basket');
  });
});
