// Shared behavior for skill-level dropdowns and basket-add buttons.
// Used by skills-home.html (dynamically-rendered search results) and
// skill-group.html (server-rendered forms) via document-level delegation.

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
