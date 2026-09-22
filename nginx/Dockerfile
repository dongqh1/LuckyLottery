FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY html/hmall-portal /usr/share/nginx/html/hmall-portal
COPY html/hmall-admin /usr/share/nginx/html/hmall-admin

EXPOSE 18080 18081
STOPSIGNAL SIGQUIT
