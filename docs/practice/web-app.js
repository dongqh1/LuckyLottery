(() => {
  "use strict";

  const payload = JSON.parse(document.getElementById("exercise-data").textContent);
  const questions = payload.questions;
  const storageKey = "hmall-lottery-practice-v2";
  const services = [
    { id: "all", label: "全部服务" },
    { id: "lottery", label: "抽奖服务" },
    { id: "points", label: "积分服务" },
    { id: "coupon", label: "优惠券服务" },
    { id: "membership", label: "会员服务" },
    { id: "fulfillment", label: "履约服务" }
  ];
  const byId = new Map(questions.map(question => [question.id, question]));
  const serviceNames = new Map(services.map(service => [service.id, service.label]));
  const element = id => document.getElementById(id);
  let state = { drafts: {}, completed: [], selected: questions[0].id };
  let storageAvailable = true;
  let selectedService = "all";
  let query = "";
  let activeId = questions[0].id;
  let visible = questions;
  let toastTimer;

  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || "null");
    if (saved && typeof saved === "object") {
      if (saved.drafts && typeof saved.drafts === "object" && !Array.isArray(saved.drafts)) {
        state.drafts = Object.fromEntries(Object.entries(saved.drafts).filter(([id, text]) => byId.has(id) && typeof text === "string"));
      }
      if (Array.isArray(saved.completed)) state.completed = [...new Set(saved.completed.filter(id => byId.has(id)))];
      if (byId.has(saved.selected)) state.selected = saved.selected;
    }
    activeId = state.selected;
  } catch (_) {
    storageAvailable = false;
  }

  function make(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function toast(message) {
    clearTimeout(toastTimer);
    element("toast").textContent = message;
    element("toast").hidden = false;
    toastTimer = setTimeout(() => { element("toast").hidden = true; }, 3200);
  }

  function save() {
    state.selected = activeId;
    try {
      localStorage.setItem(storageKey, JSON.stringify(state));
      storageAvailable = true;
      return true;
    } catch (_) {
      storageAvailable = false;
      element("save-status").textContent = "无法自动保存，请导出草稿";
      return false;
    }
  }

  function hasDraft(id) {
    return Object.hasOwn(state.drafts, id) && state.drafts[id] !== byId.get(id).starter;
  }

  function updateProgress() {
    const count = state.completed.length;
    element("progress-count").textContent = `${count} / ${questions.length}`;
    element("progress-bar").style.width = `${count / questions.length * 100}%`;
    const completed = state.completed.includes(activeId);
    element("mark-done").textContent = completed ? "已完成 · 点击取消" : "标记已完成";
    element("mark-done").setAttribute("aria-pressed", String(completed));
    element("question-list").querySelectorAll("[data-question-id]").forEach(button => {
      const id = button.dataset.questionId;
      const label = state.completed.includes(id) ? "已完成" : hasDraft(id) ? "有草稿" : "";
      button.querySelector(".q-state").textContent = label;
    });
  }

  function renderServices() {
    const nav = element("service-nav");
    nav.replaceChildren();
    services.forEach(service => {
      const button = make("button", "service-button" + (service.id === selectedService ? " active" : ""));
      button.type = "button";
      button.dataset.service = service.id;
      button.setAttribute("aria-pressed", String(service.id === selectedService));
      button.append(make("span", "", service.label), make("span", "", String(service.id === "all" ? questions.length : questions.filter(q => q.service === service.id).length)));
      button.addEventListener("click", () => {
        selectedService = service.id;
        renderServices();
        filterQuestions();
      });
      nav.append(button);
    });
  }

  function renderList() {
    const list = element("question-list");
    list.replaceChildren();
    visible.forEach(question => {
      const li = make("li");
      const button = make("button", "question-button" + (question.id === activeId ? " active" : ""));
      button.type = "button";
      button.dataset.questionId = question.id;
      if (question.id === activeId) button.setAttribute("aria-current", "true");
      const meta = make("span", "q-meta");
      meta.append(make("span", "", question.id), make("span", "q-state", ""));
      button.append(meta, make("span", "q-title", question.title));
      button.addEventListener("click", () => select(question.id));
      li.append(button);
      list.append(li);
    });
    element("result-count").textContent = `${visible.length} 题`;
    updateProgress();
  }

  function fillList(id, values) {
    element(id).replaceChildren(...values.map(value => make("li", "", value)));
  }

  function filterQuestions() {
    const search = query.trim().toLocaleLowerCase();
    visible = questions.filter(question => {
      if (selectedService !== "all" && question.service !== selectedService) return false;
      const terms = [question.id, serviceNames.get(question.service), question.title, question.purpose, question.component, ...question.tags, ...question.sources.map(source => source.path)].join(" ").toLocaleLowerCase();
      return !search || terms.includes(search);
    });
    element("empty-state").hidden = visible.length !== 0;
    element("question-panel").hidden = visible.length === 0;
    if (visible.length && !visible.some(question => question.id === activeId)) activeId = visible[0].id;
    renderList();
    if (visible.length) renderQuestion();
  }

  function renderQuestion() {
    const question = byId.get(activeId);
    element("question-service").textContent = serviceNames.get(question.service);
    element("question-id").textContent = question.id;
    element("question-title").textContent = question.title;
    element("question-purpose").textContent = question.purpose;
    element("question-level").textContent = question.level;
    element("question-minutes").textContent = `建议 ${question.minutes} 分钟`;
    element("question-component").textContent = question.component;
    element("question-scope").textContent = `代码范围：${question.scope}`;
    element("code-language").textContent = question.language;
    element("question-tags").replaceChildren(...question.tags.map(tag => make("span", "tag", tag)));
    const sourceList = element("source-list");
    sourceList.replaceChildren();
    question.sources.forEach(source => {
      const row = make("div", "source-entry");
      const container = make("div");
      const path = `${payload.sourceRoot}/${source.path}`;
      const link = make("a", "source-code-path", path);
      link.href = `file:///${path}`;
      link.target = "_blank";
      link.rel = "noopener";
      container.append(link, make("span", "source-symbol", `${source.symbol} · 第 ${source.line} 行`));
      const copy = make("button", "copy-path", "复制路径");
      copy.type = "button";
      copy.addEventListener("click", () => copyText(path, "已复制文件路径"));
      row.append(container, copy);
      sourceList.append(row);
    });
    fillList("question-context", question.context);
    fillList("question-requirements", question.requirements);
    fillList("answer-explanation", question.explanation);
    fillList("question-checks", question.checks);
    element("practice-editor").value = Object.hasOwn(state.drafts, activeId) ? state.drafts[activeId] : question.starter;
    element("answer-code").textContent = question.answer;
    element("answer-details").open = false;
    element("save-status").textContent = storageAvailable ? (hasDraft(activeId) ? "已恢复本题草稿" : "未开始 · 输入后自动保存") : "无法自动保存，请导出草稿";
    const index = visible.findIndex(item => item.id === activeId);
    element("question-position").textContent = `${index + 1} / ${visible.length}`;
    element("previous-question").disabled = index <= 0;
    element("next-question").disabled = index >= visible.length - 1;
    updateProgress();
    save();
  }

  function select(id) {
    activeId = id;
    renderList();
    renderQuestion();
  }

  async function copyText(text, message) {
    try {
      if (!navigator.clipboard) throw new Error("Clipboard unavailable");
      await navigator.clipboard.writeText(text);
      toast(message);
    } catch (_) {
      const field = make("textarea", "clipboard-fallback");
      field.value = text;
      document.body.append(field);
      field.select();
      let success = false;
      try { success = document.execCommand("copy"); } catch (_) { /* Show a manual-copy direction below. */ }
      field.remove();
      toast(success ? message : "复制失败，请选择代码后按 Ctrl+C");
    }
  }

  function recordDraft() {
    state.drafts[activeId] = element("practice-editor").value;
    if (save()) element("save-status").textContent = "本题草稿已保存";
    updateProgress();
  }

  element("practice-editor").addEventListener("input", recordDraft);
  element("practice-editor").addEventListener("keydown", event => {
    if (event.key === "Tab" && !event.shiftKey) {
      event.preventDefault();
      const editor = event.currentTarget;
      editor.setRangeText("    ", editor.selectionStart, editor.selectionEnd, "end");
      recordDraft();
    }
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
      event.preventDefault();
      recordDraft();
      toast(storageAvailable ? "当前草稿已保存" : "请使用导出草稿保存代码");
    }
  });
  element("search-input").addEventListener("input", event => { query = event.currentTarget.value; filterQuestions(); });
  element("clear-filters").addEventListener("click", () => { selectedService = "all"; query = ""; element("search-input").value = ""; renderServices(); filterQuestions(); });
  element("copy-practice").addEventListener("click", () => copyText(element("practice-editor").value, "已复制你的代码"));
  element("copy-answer").addEventListener("click", () => copyText(byId.get(activeId).answer, "已复制参考答案"));
  element("reset-draft").addEventListener("click", () => {
    if (!confirm("清除本题代码草稿并恢复题目骨架？本题完成标记也会取消。")) return;
    delete state.drafts[activeId];
    state.completed = state.completed.filter(id => id !== activeId);
    renderQuestion();
    renderList();
    toast("已恢复本题骨架");
  });
  element("mark-done").addEventListener("click", () => {
    state.completed = state.completed.includes(activeId) ? state.completed.filter(id => id !== activeId) : [...state.completed, activeId];
    save();
    updateProgress();
  });
  element("export-drafts").addEventListener("click", () => {
    const content = JSON.stringify({ version: 2, exportedAt: new Date().toISOString(), ...state }, null, 2);
    const url = URL.createObjectURL(new Blob([content], { type: "application/json;charset=utf-8" }));
    const link = make("a");
    link.href = url;
    link.download = `抽奖微服务练习草稿-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast("已导出浏览器草稿");
  });
  ["previous-question", "next-question"].forEach((id, direction) => {
    element(id).addEventListener("click", () => {
      const index = visible.findIndex(question => question.id === activeId) + (direction === 0 ? -1 : 1);
      if (visible[index]) select(visible[index].id);
    });
  });

  renderServices();
  filterQuestions();
})();
