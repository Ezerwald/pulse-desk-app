const API = {
    comments: '/comments',
    tickets:  '/tickets',
};

// ── DOM References ───────────────────────────────────────────────────────
const commentForm     = document.getElementById('commentForm');
const submitBtn       = document.getElementById('submitBtn');
const submitLabel     = document.getElementById('submitLabel');
const submitSpinner   = document.getElementById('submitSpinner');
const resultBanner    = document.getElementById('resultBanner');
const commentsList    = document.getElementById('commentsList');
const ticketsList     = document.getElementById('ticketsList');
const totalComments   = document.getElementById('totalComments');
const totalTickets    = document.getElementById('totalTickets');
const modal           = document.getElementById('modal');
const modalTitle      = document.getElementById('modalTitle');
const modalBody       = document.getElementById('modalBody');
const modalClose      = document.getElementById('modalClose');
const refreshBtn      = document.getElementById('refreshComments');

// ── Theme Toggle ─────────────────────────────────────────────────────────
(function () {
    const toggle = document.querySelector('[data-theme-toggle]');
    const root   = document.documentElement;
    let   theme  = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';

    root.setAttribute('data-theme', theme);
    updateToggleIcon(toggle, theme);

    toggle.addEventListener('click', () => {
        theme = theme === 'dark' ? 'light' : 'dark';
        root.setAttribute('data-theme', theme);
        updateToggleIcon(toggle, theme);
    });

    function updateToggleIcon(btn, t) {
        btn.setAttribute('aria-label', `Switch to ${t === 'dark' ? 'light' : 'dark'} mode`);
        btn.innerHTML = t === 'dark'
            ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                 <circle cx="12" cy="12" r="5"/>
                 <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42
                          M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
               </svg>`
            : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                 <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
               </svg>`;
    }
})();

// ── Fetch Helpers ────────────────────────────────────────────────────────
async function apiFetch(url, options = {}) {
    const res = await fetch(url, {
        headers: { 'Content-Type': 'application/json' },
        ...options,
    });
    const data = await res.json();
    if (!res.ok) throw { status: res.status, data };
    return data;
}

// ── Load Data ────────────────────────────────────────────────────────────
async function loadComments() {
    try {
        const comments = await apiFetch(API.comments);
        renderComments(comments);
        animateCounter(totalComments, comments.length);
    } catch (err) {
        commentsList.innerHTML = renderError('Could not load comments.');
    }
}

async function loadTickets() {
    try {
        const tickets = await apiFetch(API.tickets);
        renderTickets(tickets);
        animateCounter(totalTickets, tickets.length);
    } catch (err) {
        ticketsList.innerHTML = renderError('Could not load tickets.');
    }
}

function loadAll() {
    loadComments();
    loadTickets();
}

// ── Render: Comments ─────────────────────────────────────────────────────
function renderComments(comments) {
    if (comments.length === 0) {
        commentsList.innerHTML = emptyState(
            'No comments yet',
            'Submit your first comment using the form.'
        );
        return;
    }

    commentsList.innerHTML = [...comments]
        .reverse()
        .map(renderCommentCard)
        .join('');

    commentsList.querySelectorAll('.comment-card').forEach(card => {
        card.addEventListener('click', () => {
            const id = card.dataset.id;
            const comment = comments.find(c => String(c.id) === id);
            if (comment) openCommentModal(comment);
        });
    });
}

function renderCommentCard(c) {
    const ticketBadge = c.hasTicket
        ? `<span class="badge badge-ticket">🎫 Ticket</span>`
        : `<span class="badge badge-no-ticket">No ticket</span>`;

    return `
        <div class="comment-card" data-id="${c.id}" role="button"
             tabindex="0" aria-label="View comment by ${escHtml(c.author)}">
            <div class="comment-card-header">
                <span class="comment-author">${escHtml(c.author)}</span>
                ${ticketBadge}
            </div>
            <p class="comment-text">${escHtml(c.text)}</p>
            <div class="comment-meta">
                <span class="badge badge-channel">${escHtml(c.channel)}</span>
                <span style="font-size:var(--text-xs);color:var(--color-text-faint)">
                    ${formatDate(c.createdAt)}
                </span>
            </div>
        </div>`;
}

// ── Render: Tickets ───────────────────────────────────────────────────────
function renderTickets(tickets) {
    if (tickets.length === 0) {
        ticketsList.innerHTML = emptyState(
            'No tickets yet',
            'Tickets are created automatically when comments describe issues.'
        );
        return;
    }

    ticketsList.innerHTML = [...tickets]
        .reverse()
        .map(renderTicketCard)
        .join('');

    ticketsList.querySelectorAll('.ticket-card').forEach(card => {
        card.addEventListener('click', () => {
            const id = card.dataset.id;
            openTicketModal(tickets.find(t => String(t.id) === id));
        });
    });
}

function renderTicketCard(t) {
    return `
        <div class="ticket-card" data-id="${t.id}" role="button"
             tabindex="0" aria-label="View ticket: ${escHtml(t.title)}">
            <p class="ticket-title">${escHtml(t.title)}</p>
            <div class="ticket-meta">
                <span class="badge badge-${t.category.toLowerCase()}">${t.category}</span>
                <span class="badge badge-${t.priority.toLowerCase()}">${t.priority}</span>
            </div>
        </div>`;
}

// ── Form Submission ───────────────────────────────────────────────────────
commentForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const author  = document.getElementById('author').value.trim();
    const text    = document.getElementById('text').value.trim();
    const channel = document.getElementById('channel').value;

    let valid = true;
    if (!author) {
        showFieldError('authorError', 'Author name is required');
        document.getElementById('author').classList.add('invalid');
        valid = false;
    } else {
        clearFieldError('authorError');
        document.getElementById('author').classList.remove('invalid');
    }

    if (text.length < 5) {
        showFieldError('textError', 'Comment must be at least 5 characters');
        document.getElementById('text').classList.add('invalid');
        valid = false;
    } else {
        clearFieldError('textError');
        document.getElementById('text').classList.remove('invalid');
    }

    if (!valid) return;

    setSubmitLoading(true);
    hideBanner();

    try {
        const result = await apiFetch(API.comments, {
            method: 'POST',
            body: JSON.stringify({ author, text, channel }),
        });

        if (result.hasTicket && result.ticket) {
            const t = result.ticket;
            showBanner('success',
                `✅ Ticket created! <strong>${escHtml(t.title)}</strong> — 
                 ${t.category} · ${t.priority} priority`
            );
        } else {
            showBanner('no-ticket',
                `💬 Comment saved. The AI classified this as general feedback — no ticket needed.`
            );
        }

        commentForm.reset();
        loadAll();

    } catch (err) {
        if (err.data?.errors) {
            Object.entries(err.data.errors).forEach(([field, msg]) => {
                showFieldError(field + 'Error', msg);
                document.getElementById(field)?.classList.add('invalid');
            });
        } else {
            showBanner('error', '❌ Something went wrong. Please try again.');
        }
    } finally {
        setSubmitLoading(false);
    }
});

refreshBtn.addEventListener('click', loadAll);

// ── Modal ─────────────────────────────────────────────────────────────────
function openCommentModal(c) {
    modalTitle.textContent = `Comment #${c.id}`;
    modalBody.innerHTML = `
        ${modalField('Author',  escHtml(c.author))}
        ${modalField('Channel', escHtml(c.channel))}
        ${modalField('Submitted', formatDate(c.createdAt))}
        <div class="modal-divider"></div>
        ${modalField('Comment', `<span style="white-space:pre-wrap">${escHtml(c.text)}</span>`)}
        ${c.ticket ? `
            <div class="modal-divider"></div>
            <p class="modal-field-label" style="margin-bottom:var(--space-3)">
                Generated Ticket
            </p>
            ${modalField('Title',    escHtml(c.ticket.title))}
            ${modalField('Category',
        `<span class="badge badge-${c.ticket.category.toLowerCase()}">${c.ticket.category}</span>`)}
            ${modalField('Priority',
        `<span class="badge badge-${c.ticket.priority.toLowerCase()}">${c.ticket.priority}</span>`)}
            ${modalField('Summary',  escHtml(c.ticket.summary))}
        ` : `
            <div class="modal-divider"></div>
            <p style="font-size:var(--text-sm);color:var(--color-text-muted)">
                No ticket was generated for this comment.
            </p>
        `}`;

    openModal();
}

function openTicketModal(t) {
    if (!t) return;
    modalTitle.textContent = `Ticket #${t.id}`;
    modalBody.innerHTML = `
        ${modalField('Title',    escHtml(t.title))}
        ${modalField('Category',
        `<span class="badge badge-${t.category.toLowerCase()}">${t.category}</span>`)}
        ${modalField('Priority',
        `<span class="badge badge-${t.priority.toLowerCase()}">${t.priority}</span>`)}
        <div class="modal-divider"></div>
        ${modalField('Summary',   escHtml(t.summary))}
        ${modalField('Comment ID', `#${t.commentId}`)}
        ${modalField('Created',    formatDate(t.createdAt))}`;

    openModal();
}

function modalField(label, value) {
    return `<div class="modal-field">
        <p class="modal-field-label">${label}</p>
        <div class="modal-field-value">${value}</div>
    </div>`;
}

function openModal()  {
    modal.classList.remove('hidden');
    document.body.style.overflow = 'hidden';
    modalClose.focus();
}

function closeModal() {
    modal.classList.add('hidden');
    document.body.style.overflow = '';
}

modalClose.addEventListener('click', closeModal);
modal.addEventListener('click', e => { if (e.target === modal) closeModal(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

// ── UI Helpers ────────────────────────────────────────────────────────────
function setSubmitLoading(loading) {
    submitBtn.disabled = loading;
    submitLabel.textContent  = loading ? 'Analyzing...' : 'Analyze & Submit';
    submitSpinner.classList.toggle('hidden', !loading);
}

function showBanner(type, html) {
    resultBanner.className = `result-banner ${type}`;
    resultBanner.innerHTML = html;
    resultBanner.classList.remove('hidden');
}

function hideBanner() { resultBanner.classList.add('hidden'); }

function showFieldError(id, msg) {
    const el = document.getElementById(id);
    if (el) el.textContent = msg;
}

function clearFieldError(id) {
    const el = document.getElementById(id);
    if (el) el.textContent = '';
}

function emptyState(title, desc) {
    return `<div class="empty-state">
        <svg width="36" height="36" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <strong>${title}</strong>
        <p>${desc}</p>
    </div>`;
}

function renderError(msg) {
    return `<p style="font-size:var(--text-sm);color:var(--color-error);
                      padding:var(--space-4);text-align:center">${msg}</p>`;
}

function animateCounter(el, target) {
    const current = parseInt(el.textContent) || 0;
    if (current === target) return;
    const step = target > current ? 1 : -1;
    let val = current;
    const interval = setInterval(() => {
        val += step;
        el.textContent = val;
        // Pulse animation
        el.style.transform = 'scale(1.2)';
        setTimeout(() => el.style.transform = '', 150);
        if (val === target) clearInterval(interval);
    }, 60);
}

function formatDate(isoString) {
    if (!isoString) return '';
    return new Date(isoString).toLocaleString(undefined, {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function escHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ── Boot ──────────────────────────────────────────────────────────────────
loadAll();